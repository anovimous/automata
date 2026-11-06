import base64
import json
from collections.abc import AsyncIterator, Callable
from typing import Any
from uuid import UUID

import pytest
import respx

from automata_mcp.core.client import CoreClient
from automata_mcp.core.models.jobs import HttpJob
from automata_mcp.services import jobs as job_service
from helpers import chunked_response, stream_of

BASE = "http://core.test:8080"
CTX = UUID("6f1c0a52-0000-4000-8000-000000000001")
CP = f"/api/agent-contexts/uuid/{CTX}"


@pytest.fixture
async def core() -> AsyncIterator[respx.MockRouter]:
    with respx.mock(base_url=BASE, assert_all_called=False) as router:
        yield router


@pytest.fixture
async def client() -> AsyncIterator[CoreClient]:
    async with CoreClient(BASE, get_retries=0) as instance:
        yield instance


def sample(load_fixture: Callable[[str], Any], **changes: Any) -> dict[str, Any]:
    return load_fixture("job_detailed") | changes


def as_job(load_fixture: Callable[[str], Any], **changes: Any) -> HttpJob:
    return HttpJob.model_validate(sample(load_fixture, **changes))


# ---------------------------------------------------------------------------- what to do next


def test_an_unapproved_job_needs_a_human(load_fixture: Callable[[str], Any]) -> None:
    step = job_service.next_step_for_new_job(as_job(load_fixture, approved=False))

    assert step == (
        "needs human approval: a person must approve job 31; you cannot queue it until then. "
        "Tell the user."
    )


@pytest.mark.parametrize(
    ("status", "expected"),
    [
        ("READY", "approved and ready: call queue_job."),
        ("FAILED", "approved, but the config files failed: call retry_job_preparation."),
        (None, "approved: call queue_job once configFileStatus is READY."),
    ],
)
def test_an_approved_job_goes_on_by_its_files(
    load_fixture: Callable[[str], Any], status: str | None, expected: str
) -> None:
    job = as_job(load_fixture, approved=True, configFileStatus=status)

    assert job_service.next_step_for_new_job(job) == expected


def test_an_approved_job_whose_files_are_preparing_waits(
    load_fixture: Callable[[str], Any],
) -> None:
    job = as_job(load_fixture, approved=True, configFileStatus="PREPARING")

    assert (
        "until configFileStatus is READY, then call queue_job"
        in job_service.next_step_for_new_job(job)
    )


def test_the_summary_has_what_was_chosen(load_fixture: Callable[[str], Any]) -> None:
    assert job_service.summary_of(as_job(load_fixture)) == {
        "routine": "fuzz",
        "rate": 20,
        "priority": 1,
        "verbosity": "SUMMARIZED",
    }


def test_the_summary_leaves_out_what_is_missing(load_fixture: Callable[[str], Any]) -> None:
    job = as_job(load_fixture, routine=None, rate=None, priority=None, verbosity=None)

    assert job_service.summary_of(job) == {}


# ---------------------------------------------------------------------------- create_job


async def create(client: CoreClient, **overrides: Any) -> dict[str, Any]:
    arguments: dict[str, Any] = {
        "request_id": 7,
        "routine_id": 1,
        "ai_description": "Fuzz the id",
        "wordlist_ids": [3],
        "duration": None,
        "rate": None,
        "verbosity": None,
        "priority": None,
        "custom_config": None,
    }
    return await job_service.create_job(client, CTX, **(arguments | overrides))


async def test_create_posts_the_job_and_reports_what_was_created(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    post = core.post(f"{CP}/jobs").respond(201, headers={"Location": f"{CP}/jobs/31"})
    core.get(f"{CP}/jobs/31").respond(200, json=sample(load_fixture))

    result = await create(client, duration="SHORT", rate=20, verbosity="SUMMARIZED", priority=1)

    assert json.loads(post.calls.last.request.content) == {
        "requestId": 7,
        "aiDescription": "Fuzz the id",
        "routineId": 1,
        "wordlistsIds": [3],
        "duration": "SHORT",
        "rate": 20,
        "verbosity": "SUMMARIZED",
        "priority": 1,
    }
    assert result == {
        "jobId": 31,
        "state": "DRAFT",
        "approved": False,
        "configFileStatus": "READY",
        "summary": {"routine": "fuzz", "rate": 20, "priority": 1, "verbosity": "SUMMARIZED"},
        "next_step": "needs human approval: a person must approve job 31; you cannot queue it "
        "until then. Tell the user.",
    }


async def test_create_converts_raw_request_text_and_calls_nothing_else(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    post = core.post(f"{CP}/jobs").respond(201, headers={"Location": f"{CP}/jobs/31"})
    core.get(f"{CP}/jobs/31").respond(200, json=sample(load_fixture, approved=True))

    await create(
        client,
        custom_config={
            "rawRequestText": "GET /api/users/FUZZ HTTP/1.1\nHost: api.example.com\n\n",
            "flags": [],
        },
    )

    sent = json.loads(post.calls.last.request.content)["customConfig"]
    assert "rawRequestText" not in sent
    assert base64.b64decode(sent["rawRequestBase64"]) == (
        b"GET /api/users/FUZZ HTTP/1.1\r\nHost: api.example.com\r\n\r\n"
    )
    assert sent["flags"] == []
    assert [(c.request.method, c.request.url.path) for c in core.calls] == [
        ("POST", f"{CP}/jobs"),
        ("GET", f"{CP}/jobs/31"),
    ]


async def test_create_sends_an_empty_wordlist_list(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    post = core.post(f"{CP}/jobs").respond(201, headers={"Location": f"{CP}/jobs/31"})
    core.get(f"{CP}/jobs/31").respond(200, json=sample(load_fixture))

    await create(client, wordlist_ids=[])

    assert json.loads(post.calls.last.request.content)["wordlistsIds"] == []


async def test_a_job_that_was_created_but_cannot_be_read_back_is_still_reported(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post(f"{CP}/jobs").respond(201, headers={"Location": f"{CP}/jobs/31"})
    core.get(f"{CP}/jobs/31").respond(500, text="database down")

    result = await create(client)

    assert result["jobId"] == 31
    assert "could not be read back" in result["note"]
    assert "database down" in result["note"]
    assert "state" not in result


# ---------------------------------------------------------------------------- queue, retry, cancel


async def test_queue_reports_the_state_after_queuing(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core.post(f"{CP}/jobs/31/queue").respond(204)
    core.get(f"{CP}/jobs/31").respond(200, json=sample(load_fixture, currentState="TOQUEUE"))

    result = await job_service.queue_job(client, CTX, 31)

    assert result["currentState"] == "TOQUEUE"
    assert "requestedState" not in result
    assert "about every 60 seconds" in result["next_step"]
    assert "wait_for_jobs" in result["next_step"]


async def test_queue_shows_a_state_that_was_requested_meanwhile(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core.post(f"{CP}/jobs/31/queue").respond(204)
    core.get(f"{CP}/jobs/31").respond(
        200, json=sample(load_fixture, currentState="TOQUEUE", requestedState="CANCELED")
    )

    result = await job_service.queue_job(client, CTX, 31)

    assert result["requestedState"] == "CANCELED"


async def test_queue_still_succeeds_when_the_job_cannot_be_read_back(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post(f"{CP}/jobs/31/queue").respond(204)
    core.get(f"{CP}/jobs/31").respond(404, text="gone")

    result = await job_service.queue_job(client, CTX, 31)

    assert result["jobId"] == 31
    assert "note" in result
    assert "currentState" not in result


async def test_retry_says_the_files_are_being_built(
    client: CoreClient, core: respx.MockRouter
) -> None:
    route = core.post(f"{CP}/jobs/31/retry-preparation").respond(202)

    result = await job_service.retry_preparation(client, CTX, 31)

    assert route.call_count == 1
    assert result["accepted"] is True
    assert "configFileStatus is READY" in result["next_step"]


@pytest.mark.parametrize(
    ("changes", "expected"),
    [
        ({"currentState": "CANCELED"}, "The job is canceled."),
        (
            {"currentState": "RUNNING", "requestedState": "CANCELED"},
            "Cancellation requested: the worker carries it out the next time it polls. "
            "Check get_job.",
        ),
        ({"currentState": "RUNNING"}, "Check get_job to see where the job is."),
    ],
)
async def test_cancel_says_what_happened(
    client: CoreClient,
    core: respx.MockRouter,
    load_fixture: Callable[[str], Any],
    changes: dict[str, str],
    expected: str,
) -> None:
    core.post(f"{CP}/jobs/31/cancel").respond(204)
    core.get(f"{CP}/jobs/31").respond(200, json=sample(load_fixture, **changes))

    result = await job_service.cancel_job(client, CTX, 31)

    assert result["next_step"] == expected
    assert result["currentState"] == changes["currentState"]
    assert result.get("requestedState") == changes.get("requestedState")


async def test_cancel_still_succeeds_when_the_job_cannot_be_read_back(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post(f"{CP}/jobs/31/cancel").respond(204)
    core.get(f"{CP}/jobs/31").respond(500, text="down")

    result = await job_service.cancel_job(client, CTX, 31)

    assert result["next_step"] == "Check get_job."
    assert "could not be read back" in result["note"]


# ---------------------------------------------------------------------------- results


async def test_a_result_is_read_in_parts() -> None:
    response = chunked_response(
        [b'{"results": [1, 2, 3]}'], headers={"Content-Type": "application/json"}
    )

    result = await job_service.read_result(
        stream_of(response), offset=2, max_bytes=9, default_max_bytes=100, fetch_cap=1000
    )

    assert result["text"] == 'results":'
    assert result["contentType"] == "application/json"
    assert result["truncated"] is True
    assert result["nextOffset"] == 11


async def test_a_result_uses_the_default_size_and_never_the_fetch_cap() -> None:
    response = chunked_response([b"x" * 500], headers={"Content-Type": "text/plain"})

    result = await job_service.read_result(
        stream_of(response), offset=0, max_bytes=None, default_max_bytes=100, fetch_cap=300
    )
    bigger = await job_service.read_result(
        stream_of(chunked_response([b"x" * 500], headers={"Content-Type": "text/plain"})),
        offset=0,
        max_bytes=10_000,
        default_max_bytes=100,
        fetch_cap=300,
    )

    assert result["returnedBytes"] == 100
    assert bigger["returnedBytes"] == 300
    assert "reads at most 300 bytes" in bigger["note"]
