"""What the job tools do beyond one call to the core: read a job back, say what to do next.

The core owns every rule about jobs. These functions only pass arguments on, read the job again
after an action and turn its fields into guidance for the agent.
"""

from typing import Any
from uuid import UUID

from automata_mcp.core.client import CoreClient
from automata_mcp.core.errors import CoreError
from automata_mcp.core.models.enums import ConfigFileStatus, JobState
from automata_mcp.core.models.jobs import AiJobCreation, HttpJob
from automata_mcp.services.bodies import Stream, read_body, resolve_max_bytes
from automata_mcp.services.config_validation import expand_raw_request_text

SCHEDULER_NOTE = "the scheduler picks TOQUEUE jobs about every 60 seconds"


async def read_back(
    core: CoreClient, context_uuid: UUID, job_id: int
) -> tuple[HttpJob | None, str | None]:
    """The job as it is now, or why it could not be read (the action that came before worked)."""
    try:
        return await core.get_job(context_uuid, job_id), None
    except CoreError as error:
        return None, f"The action succeeded, but job {job_id} could not be read back: {error}"


def summary_of(job: HttpJob) -> dict[str, Any]:
    summary: dict[str, Any] = {
        "routine": job.routine.key if job.routine else None,
        "rate": job.rate,
        "priority": job.priority,
        "verbosity": job.verbosity,
    }
    return {key: value for key, value in summary.items() if value is not None}


def next_step_for_new_job(job: HttpJob) -> str:
    """What the agent does with a job that was just created (a DRAFT)."""
    if not job.approved:
        return (
            f"needs human approval: a person must approve job {job.job_id}; you cannot queue it "
            "until then. Tell the user."
        )
    if job.config_file_status == ConfigFileStatus.READY:
        return "approved and ready: call queue_job."
    if job.config_file_status == ConfigFileStatus.PREPARING:
        return (
            "approved: the config files are being prepared; check get_job until "
            "configFileStatus is READY, then call queue_job."
        )
    if job.config_file_status == ConfigFileStatus.FAILED:
        return "approved, but the config files failed: call retry_job_preparation."
    return "approved: call queue_job once configFileStatus is READY."


async def create_job(
    core: CoreClient,
    context_uuid: UUID,
    *,
    request_id: int,
    routine_id: int,
    ai_description: str,
    wordlist_ids: list[int],
    duration: str | None,
    rate: int | None,
    verbosity: str | None,
    priority: int | None,
    custom_config: dict[str, Any] | None,
) -> dict[str, Any]:
    """Create the job (the core enforces every rule) and report what was created."""
    creation = AiJobCreation.model_validate(
        {
            "requestId": request_id,
            "aiDescription": ai_description,
            "routineId": routine_id,
            "duration": duration,
            "wordlistsIds": wordlist_ids,
            "rate": rate,
            "verbosity": verbosity,
            "priority": priority,
            "customConfig": expand_raw_request_text(custom_config),
        }
    )
    job_id = await core.create_job(context_uuid, creation)
    job, problem = await read_back(core, context_uuid, job_id)
    if job is None:
        return {"jobId": job_id, "note": problem}
    return {
        "jobId": job_id,
        "state": job.current_state,
        "approved": job.approved,
        "configFileStatus": job.config_file_status,
        "summary": summary_of(job),
        "next_step": next_step_for_new_job(job),
    }


async def queue_job(core: CoreClient, context_uuid: UUID, job_id: int) -> dict[str, Any]:
    await core.queue_job(context_uuid, job_id)
    job, problem = await read_back(core, context_uuid, job_id)
    result: dict[str, Any] = {
        "jobId": job_id,
        "next_step": f"Queued ({SCHEDULER_NOTE}): use wait_for_jobs to follow it.",
    }
    if job is None:
        result["note"] = problem
    else:
        result["currentState"] = job.current_state
        if job.requested_state is not None:
            result["requestedState"] = job.requested_state
    return result


async def retry_preparation(core: CoreClient, context_uuid: UUID, job_id: int) -> dict[str, Any]:
    await core.retry_job_preparation(context_uuid, job_id)
    return {
        "jobId": job_id,
        "accepted": True,
        "next_step": "Accepted: the config files are being built again; check get_job until "
        "configFileStatus is READY, then call queue_job.",
    }


async def cancel_job(core: CoreClient, context_uuid: UUID, job_id: int) -> dict[str, Any]:
    await core.cancel_job(context_uuid, job_id)
    job, problem = await read_back(core, context_uuid, job_id)
    if job is None:
        return {"jobId": job_id, "note": problem, "next_step": "Check get_job."}
    result: dict[str, Any] = {"jobId": job_id, "currentState": job.current_state}
    if job.requested_state is not None:
        result["requestedState"] = job.requested_state
    if job.current_state == JobState.CANCELED:
        result["next_step"] = "The job is canceled."
    elif job.requested_state == JobState.CANCELED:
        result["next_step"] = (
            "Cancellation requested: the worker carries it out the next time it polls. "
            "Check get_job."
        )
    else:
        result["next_step"] = "Check get_job to see where the job is."
    return result


async def read_result(
    stream: Stream,
    *,
    offset: int,
    max_bytes: int | None,
    default_max_bytes: int,
    fetch_cap: int,
) -> dict[str, Any]:
    """The result of a job, text read in parts under the result cap (plan section 9.3)."""
    return await read_body(
        stream,
        offset=offset,
        max_bytes=resolve_max_bytes(max_bytes, default_max_bytes, fetch_cap),
        fetch_cap=fetch_cap,
    )
