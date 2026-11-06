from collections.abc import AsyncIterator
from typing import Any
from uuid import UUID

import httpx
import pytest
import respx

from automata_mcp.core.client import CoreClient
from automata_mcp.core.errors import CoreNotFound, CoreServerError
from automata_mcp.core.models.jobs import HttpJob
from automata_mcp.services.waiting import (
    job_snapshot,
    next_steps,
    should_stop,
    wait_for_jobs,
)

BASE = "http://core.test:8080"
CTX = UUID("6f1c0a52-0000-4000-8000-000000000001")
CP = f"/api/agent-contexts/uuid/{CTX}"


class FakeTime:
    """A clock that only moves when the code sleeps."""

    def __init__(self) -> None:
        self.now = 1000.0
        self.sleeps: list[float] = []

    def clock(self) -> float:
        return self.now

    async def sleep(self, seconds: float) -> None:
        self.sleeps.append(seconds)
        self.now += seconds


@pytest.fixture
def time_() -> FakeTime:
    return FakeTime()


@pytest.fixture
async def core() -> AsyncIterator[respx.MockRouter]:
    with respx.mock(base_url=BASE, assert_all_called=False) as router:
        yield router


@pytest.fixture
async def client() -> AsyncIterator[CoreClient]:
    async with CoreClient(BASE, get_retries=0) as instance:
        yield instance


def job_json(
    job_id: int,
    state: str = "RUNNING",
    *,
    terminal: bool = False,
    waiting: bool = False,
    approved: bool = True,
    **extra: Any,
) -> dict[str, Any]:
    return {
        "jobId": job_id,
        "currentState": state,
        "configFileStatus": "READY",
        "approved": approved,
        "terminal": terminal,
        "waitingForAction": waiting,
    } | extra


def script(
    core: respx.MockRouter, job_id: int, *answers: dict[str, Any] | int | Exception
) -> respx.Route:
    """The job answers these in turn (a dict is a job, an int a status, an exception an error).

    The last answer repeats."""
    calls = {"n": 0}

    def answer(request: httpx.Request) -> httpx.Response:
        item = answers[min(calls["n"], len(answers) - 1)]
        calls["n"] += 1
        if isinstance(item, Exception):
            raise item
        if isinstance(item, int):
            return httpx.Response(item, text="trouble")
        return httpx.Response(200, json=item)

    return core.get(f"{CP}/jobs/{job_id}").mock(side_effect=answer)


async def run(
    client: CoreClient,
    time_: FakeTime,
    ids: list[int],
    *,
    limit: float = 45,
    return_when: Any = "any",
    poll: float = 5,
    max_errors: int = 3,
    on_poll: Any = None,
) -> dict[str, Any]:
    return await wait_for_jobs(
        client,
        CTX,
        ids,
        timeout_seconds=limit,
        return_when=return_when,
        poll_seconds=poll,
        max_poll_errors=max_errors,
        on_poll=on_poll,
        sleep=time_.sleep,
        clock=time_.clock,
    )


# ---------------------------------------------------------------------------- pure helpers


def job(job_id: int = 1, **fields: Any) -> HttpJob:
    return HttpJob.model_validate(job_json(job_id, **fields))


def test_a_snapshot_keeps_only_what_is_set() -> None:
    snapshot = job_snapshot(
        job(7, state="FAILED", terminal=True, failureMessage="exit 2", failureCategory="TOOL_EXIT")
    )

    assert snapshot == {
        "jobId": 7,
        "currentState": "FAILED",
        "approved": True,
        "configFileStatus": "READY",
        "terminal": True,
        "waitingForAction": False,
        "failureCategory": "TOOL_EXIT",
        "failureMessage": "exit 2",
    }


@pytest.mark.parametrize(
    ("jobs", "return_when", "expected"),
    [
        ({1: job(1)}, "any", False),
        ({1: job(1, terminal=True)}, "any", True),
        ({1: job(1, terminal=True), 2: job(2)}, "any", True),
        ({1: job(1, terminal=True), 2: job(2)}, "all", False),
        ({1: job(1, terminal=True), 2: job(2, terminal=True)}, "all", True),
        ({1: job(1, waiting=True, state="DRAFT")}, "any", True),
        ({1: job(1, waiting=True), 2: job(2)}, "any", False),
        ({1: job(1, terminal=True), 2: job(2, waiting=True)}, "all", True),
        ({1: job(1, waiting=True), 2: job(2, waiting=True)}, "all", True),
        ({1: None}, "any", False),
        ({1: None, 2: job(2, terminal=True)}, "any", True),
        ({1: None, 2: job(2, terminal=True)}, "all", False),
        ({}, "any", True),
    ],
)
def test_should_stop(jobs: dict[int, HttpJob | None], return_when: Any, expected: bool) -> None:
    assert should_stop(jobs, return_when) is expected


def test_next_steps_follow_the_flags_and_failure_fields() -> None:
    jobs: dict[int, HttpJob | None] = {
        1: job(1, state="FINISHED", terminal=True),
        2: job(2, state="FAILED", terminal=True, failureCategory="INVALID_CONFIG"),
        3: job(3, state="DRAFT", waiting=True, approved=False),
        4: job(4),
        5: None,
    }

    steps = next_steps(jobs, timed_out=False)

    assert steps[0].startswith("Job 1 ended (FINISHED): read its result with get_job_result")
    assert steps[1].startswith("Job 2 ended with a failure: read failureCategory")
    assert steps[2].startswith("Job 3 waits for someone")
    assert steps[3] == "Job 5 could not be read; call get_job."
    assert len(steps) == 4


def test_a_timeout_says_to_call_again() -> None:
    assert next_steps({1: job(1)}, timed_out=True) == [
        "The wait timed out: call wait_for_jobs again to keep waiting."
    ]


# ---------------------------------------------------------------------------- wait_for_jobs


async def test_a_job_that_already_ended_returns_without_waiting(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    script(core, 31, job_json(31, "FINISHED", terminal=True))

    result = await run(client, time_, [31])

    assert result["terminalJobIds"] == [31]
    assert result["timedOut"] is False
    assert time_.sleeps == []


async def test_it_polls_until_a_job_ends(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    route = script(
        core,
        31,
        job_json(31, "QUEUED"),
        job_json(31, "RUNNING"),
        job_json(31, "FINISHED", terminal=True),
    )

    result = await run(client, time_, [31])

    assert route.call_count == 3
    assert time_.sleeps == [5, 5]
    assert result["jobs"][0]["currentState"] == "FINISHED"
    assert result["terminalJobIds"] == [31]


async def test_with_any_it_returns_when_one_job_ends(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    script(core, 1, job_json(1))
    script(core, 2, job_json(2), job_json(2, "FINISHED", terminal=True))

    result = await run(client, time_, [1, 2])

    assert result["terminalJobIds"] == [2]
    assert [j["currentState"] for j in result["jobs"]] == ["RUNNING", "FINISHED"]


async def test_with_all_it_waits_for_every_job(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    script(core, 1, job_json(1, "FINISHED", terminal=True))
    script(core, 2, job_json(2), job_json(2), job_json(2, "FAILED", terminal=True))

    result = await run(client, time_, [1, 2], return_when="all")

    assert result["terminalJobIds"] == [1, 2]
    assert time_.sleeps == [5, 5]


async def test_jobs_that_ended_are_not_polled_again(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    first = script(core, 1, job_json(1, "FINISHED", terminal=True))
    script(core, 2, job_json(2), job_json(2), job_json(2, "FINISHED", terminal=True))

    await run(client, time_, [1, 2], return_when="all")

    assert first.call_count == 1


async def test_it_returns_when_everything_left_waits_for_someone(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    script(core, 31, job_json(31, "DRAFT", waiting=True, approved=False))

    result = await run(client, time_, [31])

    assert result["timedOut"] is False
    assert result["waitingForActionJobIds"] == [31]
    assert result["terminalJobIds"] == []
    assert time_.sleeps == []
    assert "waits for someone" in result["next_steps"][0]


async def test_it_gives_up_when_the_time_is_up(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    route = script(core, 31, job_json(31))

    result = await run(client, time_, [31], limit=12, poll=5)

    assert result["timedOut"] is True
    assert time_.sleeps == [5, 5, 2]
    assert route.call_count == 4
    assert result["next_steps"] == ["The wait timed out: call wait_for_jobs again to keep waiting."]


async def test_the_first_poll_happens_even_with_a_tiny_timeout(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    route = script(core, 31, job_json(31))

    result = await run(client, time_, [31], limit=0.001)

    assert route.call_count == 2
    assert result["timedOut"] is True


async def test_a_failed_poll_is_skipped(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    script(core, 31, 503, job_json(31, "FINISHED", terminal=True))

    result = await run(client, time_, [31])

    assert result["terminalJobIds"] == [31]


async def test_an_unreadable_job_is_reported_when_the_wait_times_out(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    script(core, 31, 503)

    result = await run(client, time_, [31], limit=3, poll=5, max_errors=100)

    assert result["jobs"] == [{"jobId": 31}]
    assert "Job 31 could not be read" in result["next_steps"][0]


async def test_connection_errors_count_like_server_errors(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    script(core, 31, httpx.ConnectError("refused"), job_json(31, "FINISHED", terminal=True))

    result = await run(client, time_, [31])

    assert result["terminalJobIds"] == [31]


async def test_errors_in_a_row_fail_the_call(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    route = script(core, 31, 503)

    with pytest.raises(CoreServerError, match="trouble"):
        await run(client, time_, [31], max_errors=3)

    assert route.call_count == 3


async def test_a_good_poll_resets_the_error_count(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    script(core, 31, 503, 503, job_json(31), 503, 503, job_json(31, "FINISHED", terminal=True))

    result = await run(client, time_, [31], max_errors=3)

    assert result["terminalJobIds"] == [31]


async def test_an_error_that_is_not_transient_fails_at_once(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    script(core, 31, 404)

    with pytest.raises(CoreNotFound):
        await run(client, time_, [31])

    assert time_.sleeps == []


async def test_progress_is_reported_after_each_poll(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    script(core, 1, job_json(1), job_json(1, "FINISHED", terminal=True))
    reported: list[tuple[float, float, str]] = []

    async def on_poll(elapsed: float, total: float, message: str) -> None:
        reported.append((elapsed, total, message))

    await run(client, time_, [1], limit=30, on_poll=on_poll)

    assert reported == [(0.0, 30, "0 of 1 jobs ended"), (5.0, 30, "1 of 1 jobs ended")]


async def test_the_failure_fields_reach_the_agent(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    script(
        core,
        1,
        job_json(1, "FAILED", terminal=True, failureCategory="TOOL_EXIT", failureMessage="exit 2"),
    )

    result = await run(client, time_, [1])

    assert result["jobs"][0]["failureCategory"] == "TOOL_EXIT"
    assert result["jobs"][0]["failureMessage"] == "exit 2"
    assert result["next_steps"][0].startswith("Job 1 ended with a failure")


async def test_the_polls_of_one_round_run_together(
    client: CoreClient, core: respx.MockRouter, time_: FakeTime
) -> None:
    for job_id in (1, 2, 3):
        script(core, job_id, job_json(job_id, "FINISHED", terminal=True))

    result = await run(client, time_, [1, 2, 3], return_when="all")

    assert result["terminalJobIds"] == [1, 2, 3]
    assert time_.sleeps == []
