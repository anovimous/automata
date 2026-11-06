"""Waiting for jobs to end: polls the core and returns when something worth acting on happened.

The server knows no state meaning. It reads the ``terminal`` and ``waitingForAction`` flags the
core computes on each job (plan section 9.3).
"""

import asyncio
import time
from collections.abc import Awaitable, Callable, Sequence
from typing import Any, Literal
from uuid import UUID

from automata_mcp.core.client import CoreClient
from automata_mcp.core.errors import CoreError, CoreServerError, CoreUnreachable
from automata_mcp.core.models.jobs import HttpJob

ReturnWhen = Literal["any", "all"]
type OnPoll = Callable[[float, float, str], Awaitable[None]]
type Sleep = Callable[[float], Awaitable[None]]
type Clock = Callable[[], float]

TRANSIENT = (CoreServerError, CoreUnreachable)


def job_snapshot(job: HttpJob) -> dict[str, Any]:
    """What the agent needs to know about a job while waiting."""
    snapshot: dict[str, Any] = {
        "jobId": job.job_id,
        "currentState": job.current_state,
        "requestedState": job.requested_state,
        "approved": job.approved,
        "configFileStatus": job.config_file_status,
        "terminal": job.terminal,
        "waitingForAction": job.waiting_for_action,
        "failureCategory": job.failure_category,
        "failureMessage": job.failure_message,
    }
    return {key: value for key, value in snapshot.items() if value is not None}


def should_stop(jobs: dict[int, HttpJob | None], return_when: ReturnWhen) -> bool:
    """Has something happened that the agent should hear about?"""
    known = [job for job in jobs.values() if job is not None]
    terminal = [job for job in known if job.terminal]
    if return_when == "any" and terminal:
        return True
    if len(known) < len(jobs):
        return False
    pending = [job for job in known if not job.terminal]
    if not pending:
        return True
    return all(job.waiting_for_action for job in pending)


def next_steps(jobs: dict[int, HttpJob | None], timed_out: bool) -> list[str]:
    """What to do next, from the flags and failure fields of the core."""
    steps: list[str] = []
    for job_id, job in jobs.items():
        if job is None:
            steps.append(f"Job {job_id} could not be read; call get_job.")
        elif job.terminal and (job.failure_message or job.failure_category):
            steps.append(
                f"Job {job_id} ended with a failure: read failureCategory and failureMessage "
                "(get_job) and decide what to do."
            )
        elif job.terminal:
            steps.append(
                f"Job {job_id} ended ({job.current_state}): read its result with get_job_result, "
                "then record your conclusion with set_job_ai_result."
            )
        elif job.waiting_for_action:
            steps.append(
                f"Job {job_id} waits for someone: a human must approve it (approved is false) or "
                "its config files failed (configFileStatus is FAILED: retry_job_preparation)."
            )
    if timed_out:
        steps.append("The wait timed out: call wait_for_jobs again to keep waiting.")
    return steps


async def wait_for_jobs(
    core: CoreClient,
    context_uuid: UUID,
    job_ids: Sequence[int],
    *,
    timeout_seconds: float,
    return_when: ReturnWhen,
    poll_seconds: float,
    max_poll_errors: int,
    on_poll: OnPoll | None = None,
    sleep: Sleep = asyncio.sleep,
    clock: Clock = time.monotonic,
) -> dict[str, Any]:
    """Poll the jobs until one of them ends (``any``) or all do (``all``), until every job still
    running waits for someone, or until the time is up.

    A poll that fails with a core or connection error is skipped; ``max_poll_errors`` rounds with
    such an error in a row fail the call. Any other error (a job that is not in the context, for
    example) fails it at once. Jobs already ended are not polled again.
    """
    started = clock()
    deadline = started + timeout_seconds
    jobs: dict[int, HttpJob | None] = dict.fromkeys(job_ids)
    errors_in_a_row = 0
    timed_out = False

    while True:
        pending = [job_id for job_id, job in jobs.items() if job is None or not job.terminal]
        results = await asyncio.gather(
            *(core.get_job(context_uuid, job_id) for job_id in pending), return_exceptions=True
        )
        round_error: CoreError | None = None
        for job_id, result in zip(pending, results, strict=True):
            if isinstance(result, HttpJob):
                jobs[job_id] = result
            elif isinstance(result, TRANSIENT):
                round_error = result
            elif isinstance(result, BaseException):
                raise result
        errors_in_a_row = errors_in_a_row + 1 if round_error else 0
        if round_error and errors_in_a_row >= max_poll_errors:
            raise round_error

        elapsed = clock() - started
        if on_poll is not None:
            ended = sum(1 for job in jobs.values() if job is not None and job.terminal)
            await on_poll(elapsed, timeout_seconds, f"{ended} of {len(jobs)} jobs ended")
        if should_stop(jobs, return_when):
            break
        remaining = deadline - clock()
        if remaining <= 0:
            timed_out = True
            break
        await sleep(min(poll_seconds, remaining))

    return {
        "jobs": [
            job_snapshot(job) if job is not None else {"jobId": job_id}
            for job_id, job in jobs.items()
        ],
        "timedOut": timed_out,
        "terminalJobIds": [j for j, job in jobs.items() if job is not None and job.terminal],
        "waitingForActionJobIds": [
            j
            for j, job in jobs.items()
            if job is not None and not job.terminal and job.waiting_for_action
        ],
        "next_steps": next_steps(jobs, timed_out),
    }
