"""The job tools, called through MCP against a fake core."""

import base64
import json
from collections.abc import Callable
from typing import Any

import pytest
import respx
from mcp.types import CallToolResult

from automata_mcp.config import Settings
from helpers import Agent

BASE = "http://core.test:8080"
CTX = "6f1c0a52-0000-4000-8000-000000000001"
CP = f"/api/agent-contexts/uuid/{CTX}"
TOOLS = {
    "create_job",
    "list_jobs",
    "get_job",
    "list_request_jobs",
    "get_request_job",
    "get_request_job_result",
    "get_job_result",
    "wait_for_jobs",
    "queue_job",
    "retry_job_preparation",
    "set_job_ai_result",
    "cancel_job",
}
ARGUMENTS: dict[str, dict[str, Any]] = {
    "create_job": {
        "request_id": 7,
        "routine_id": 1,
        "ai_description": "d",
        "wordlist_ids": [],
    },
    "list_jobs": {},
    "get_job": {"job_id": 31},
    "list_request_jobs": {"request_id": 7},
    "get_request_job": {"request_id": 7, "job_id": 30},
    "get_request_job_result": {"request_id": 7, "job_id": 30},
    "get_job_result": {"job_id": 31},
    "wait_for_jobs": {"job_ids": [31]},
    "queue_job": {"job_id": 31},
    "retry_job_preparation": {"job_id": 31},
    "set_job_ai_result": {"job_id": 31, "ai_result": "done"},
    "cancel_job": {"job_id": 31},
}


def text_of(result: CallToolResult) -> str:
    return result.content[0].text  # type: ignore[union-attr]


def job(load_fixture: Callable[[str], Any], **changes: Any) -> dict[str, Any]:
    return load_fixture("job_detailed") | changes


async def call(agent: Agent, tool: str, **extra: Any) -> CallToolResult:
    """Call a tool with the context, its usual arguments and any overrides."""
    return await agent.call_tool(tool, {"context_uuid": CTX} | ARGUMENTS[tool] | extra)


def result_route(
    core: respx.MockRouter, path: str, content: bytes, kind: str = "application/json"
) -> respx.Route:
    return core.get(path).respond(200, content=content, headers={"Content-Type": kind})


# ---------------------------------------------------------------------------- the tool list


async def test_the_twelve_job_tools_are_registered(agent: Agent) -> None:
    names = {t.name for t in (await agent.list_tools()).tools}

    assert names >= TOOLS


async def test_annotations_say_what_each_tool_does(agent: Agent) -> None:
    tools = {t.name: t for t in (await agent.list_tools()).tools if t.name in TOOLS}

    def flags(name: str) -> tuple[Any, Any, Any]:
        annotations = tools[name].annotations
        assert annotations is not None
        assert annotations.open_world_hint is False
        return annotations.read_only_hint, annotations.destructive_hint, annotations.idempotent_hint

    for name in (
        "list_jobs",
        "get_job",
        "list_request_jobs",
        "get_request_job",
        "get_request_job_result",
        "get_job_result",
        "wait_for_jobs",
    ):
        assert flags(name)[0] is True, name
    assert flags("create_job") == (False, False, False)
    assert flags("queue_job") == (False, False, False)
    assert flags("retry_job_preparation") == (False, False, True)
    assert flags("set_job_ai_result") == (False, False, True)
    assert flags("cancel_job") == (False, True, False)


async def test_every_job_tool_requires_a_context_and_explains_itself(agent: Agent) -> None:
    for tool in (await agent.list_tools()).tools:
        if tool.name not in TOOLS:
            continue
        assert "context_uuid" in tool.input_schema["required"], tool.name
        assert "ctx" not in tool.input_schema["properties"], tool.name
        assert len(tool.description or "") > 150, tool.name


async def test_create_job_schema_offers_the_choices_of_the_core(agent: Agent) -> None:
    tool = next(t for t in (await agent.list_tools()).tools if t.name == "create_job")
    properties = tool.input_schema["properties"]

    assert set(tool.input_schema["required"]) == {
        "context_uuid",
        "request_id",
        "routine_id",
        "ai_description",
        "wordlist_ids",
    }
    assert "SHORT" in json.dumps(properties["duration"])
    assert "VERBOSE" in json.dumps(properties["verbosity"])
    assert "rawRequestBase64" in (tool.description or "")
    assert "rawRequestText" in (tool.description or "")


async def test_get_job_explains_the_lifecycle_and_the_failure_categories(agent: Agent) -> None:
    tool = next(t for t in (await agent.list_tools()).tools if t.name == "get_job")
    description = tool.description or ""

    for word in (
        "DRAFT",
        "TOQUEUE",
        "LOSTCONTACT",
        "waitingForAction",
        "INVALID_CONFIG",
        "TOOL_EXIT",
    ):
        assert word in description


async def test_wait_for_jobs_schema(agent: Agent) -> None:
    tool = next(t for t in (await agent.list_tools()).tools if t.name == "wait_for_jobs")
    properties = tool.input_schema["properties"]

    assert properties["job_ids"]["minItems"] == 1
    assert properties["return_when"]["enum"] == ["any", "all"]
    assert properties["return_when"]["default"] == "any"


# ---------------------------------------------------------------------------- create_job


async def test_create_job_creates_and_tells_what_comes_next(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    post = core_api.post(f"{CP}/jobs").respond(201, headers={"Location": f"{CP}/jobs/31"})
    core_api.get(f"{CP}/jobs/31").respond(200, json=job(load_fixture, approved=True))

    result = await call(
        agent,
        "create_job",
        wordlist_ids=[3],
        duration="SHORT",
        ai_description="Fuzz the id",
        custom_config={"rawRequestText": "GET /api/users/FUZZ HTTP/1.1\nHost: x\n\n"},
    )

    data = result.structured_content or {}
    assert data["jobId"] == 31
    assert data["state"] == "DRAFT"
    assert data["next_step"] == "approved and ready: call queue_job."
    sent = json.loads(post.calls.last.request.content)
    assert sent["duration"] == "SHORT"
    assert sent["wordlistsIds"] == [3]
    assert base64.b64decode(sent["customConfig"]["rawRequestBase64"]).startswith(
        b"GET /api/users/FUZZ HTTP/1.1\r\n"
    )


async def test_a_job_that_needs_approval_tells_the_agent_to_ask_the_user(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.post(f"{CP}/jobs").respond(201, headers={"Location": f"{CP}/jobs/31"})
    core_api.get(f"{CP}/jobs/31").respond(200, json=job(load_fixture))

    result = await call(agent, "create_job")

    assert "needs human approval" in (result.structured_content or {})["next_step"]


async def test_an_unknown_target_or_wordlist_gets_one_hint_for_both(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.post(f"{CP}/jobs").respond(404, text="Request not found in the agent context")

    result = await call(agent, "create_job")

    assert result.is_error
    assert "Request not found in the agent context" in text_of(result)
    assert "call list_targets" in text_of(result)
    assert "call list_wordlists" in text_of(result)


@pytest.mark.parametrize(
    ("status", "message", "expected"),
    [
        (422, "Host 2 has a rate limit of 0, so no job can run on it", "tell the user"),
        (400, "customConfig: unknown property 'foo'", "Fix the arguments"),
    ],
)
async def test_the_rule_the_core_enforced_is_shown(
    agent: Agent, core_api: respx.MockRouter, status: int, message: str, expected: str
) -> None:
    core_api.post(f"{CP}/jobs").respond(status, text=message)

    result = await call(agent, "create_job")

    assert result.is_error
    assert message in text_of(result)
    assert expected in text_of(result)


async def test_a_duration_the_core_does_not_have_is_refused_before_any_call(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    result = await call(agent, "create_job", duration="FOREVER")

    assert result.is_error
    assert not [c for c in core_api.calls if c.request.method == "POST"]


# ---------------------------------------------------------------------------- list and get


async def test_list_jobs_returns_a_page_and_passes_the_states(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core_api.get(f"{CP}/jobs").respond(200, json=load_fixture("job_summary_page"))

    result = await call(agent, "list_jobs", states=["FINISHED", "FAILED"], page=1, size=5)

    data = result.structured_content or {}
    assert [j["id"] for j in data["jobs"]] == [31, 30]
    assert data["jobs"][0]["waitingForAction"] is True
    assert dict(route.calls.last.request.url.params) == {
        "states": "FINISHED,FAILED",
        "page": "1",
        "size": "5",
    }


async def test_list_jobs_refuses_a_state_that_does_not_exist(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    result = await call(agent, "list_jobs", states=["ARCHIVED"])

    assert result.is_error
    assert not [c for c in core_api.calls if c.request.url.path.endswith("/jobs")]


async def test_get_job_returns_the_job_with_its_flags(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/jobs/31").respond(200, json=load_fixture("job_detailed"))

    data = (await call(agent, "get_job")).structured_content or {}

    assert data["jobId"] == 31
    assert data["terminal"] is False
    assert data["waitingForAction"] is True
    assert data["configFileStatus"] == "READY"
    assert data["routine"] == {"id": 1, "key": "fuzz"}
    assert "failureMessage" not in data


async def test_get_job_of_a_job_outside_the_context_is_not_found(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/jobs/31").respond(404, text="Job not found")

    result = await call(agent, "get_job")

    assert result.is_error
    assert "Not found, or not part of this context" in text_of(result)


async def test_list_request_jobs_shows_the_history_with_the_origin(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core_api.get(f"{CP}/requests/7/jobs").respond(
        200, json=load_fixture("request_job_summary_page")
    )

    data = (await call(agent, "list_request_jobs", states=["FAILED"])).structured_content or {}

    assert [(j["id"], j["origin"]) for j in data["jobs"]] == [(30, "OTHER_CONTEXT"), (12, "USER")]
    assert dict(route.calls.last.request.url.params)["states"] == "FAILED"


async def test_list_request_jobs_of_a_request_outside_the_context_gets_a_hint(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/requests/7/jobs").respond(
        404, text="Request not found in the agent context"
    )

    result = await call(agent, "list_request_jobs")

    assert result.is_error
    assert "list_context_requests" in text_of(result)


async def test_get_request_job_shows_what_a_past_test_did(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/requests/7/jobs/30").respond(200, json=load_fixture("request_job"))

    data = (await call(agent, "get_request_job")).structured_content or {}

    assert data["customConfig"] == {"flags": [{"key": "-mc", "value": "200"}]}
    assert data["failureCategory"] == "TOOL_EXIT"
    assert data["origin"] == "THIS_CONTEXT"


async def test_get_request_job_that_did_not_target_the_request_gets_a_hint(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/requests/7/jobs/30").respond(404, text="Job not found")

    result = await call(agent, "get_request_job")

    assert result.is_error
    assert "did not target this request" in text_of(result)


# ---------------------------------------------------------------------------- results


async def test_get_job_result_reads_the_result_in_parts(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    payload = b'{"results": [{"status": 200}]}'
    result_route(core_api, f"{CP}/jobs/31/result", payload)

    data = (await call(agent, "get_job_result", offset=1, max_bytes=9)).structured_content or {}

    assert data["text"] == '"results"'
    assert data["contentType"] == "application/json"
    assert (data["offset"], data["returnedBytes"], data["truncated"]) == (1, 9, True)
    assert data["nextOffset"] == 10
    assert data["totalBytes"] == len(payload)


async def test_get_job_result_uses_the_result_limit_by_default(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    result_route(core_api, f"{CP}/jobs/31/result", b"x" * 150_000, "text/plain")

    data = (await call(agent, "get_job_result")).structured_content or {}

    assert data["returnedBytes"] == 100_000
    assert data["truncated"] is True


async def test_a_job_that_has_not_ended_has_no_result_yet(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/jobs/31/result").respond(
        409, text="The job has to end before downloading its result"
    )

    result = await call(agent, "get_job_result")

    assert result.is_error
    assert "The job has to end before downloading its result" in text_of(result)
    assert "use wait_for_jobs, then read the result again" in text_of(result)


async def test_a_job_that_ended_without_a_result_says_where_to_look(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/jobs/31/result").respond(404, text="Result not found")

    result = await call(agent, "get_job_result")

    assert result.is_error
    assert "Result not found" in text_of(result)
    assert "ended without one" in text_of(result)
    assert "get_job" in text_of(result)


async def test_get_request_job_result_reads_from_the_history_route(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    route = result_route(
        core_api, f"{CP}/requests/7/jobs/30/result", b"line one\nline two", "text/plain"
    )

    data = (await call(agent, "get_request_job_result")).structured_content or {}

    assert route.call_count == 1
    assert data["text"] == "line one\nline two"


async def test_get_request_job_result_explains_both_failures(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/requests/7/jobs/30/result").respond(404, text="Job not found")

    not_found = await call(agent, "get_request_job_result")
    core_api.get(f"{CP}/requests/7/jobs/30/result").respond(409, text="not ended")
    not_ended = await call(agent, "get_request_job_result")

    assert "did not target this request" in text_of(not_found)
    assert "ended without one" in text_of(not_found)
    assert "use wait_for_jobs" in text_of(not_ended)


# ---------------------------------------------------------------------------- actions


async def test_queue_job_queues_and_tells_how_to_follow_it(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.post(f"{CP}/jobs/31/queue").respond(204)
    core_api.get(f"{CP}/jobs/31").respond(
        200, json=job(load_fixture, currentState="TOQUEUE", approved=True)
    )

    data = (await call(agent, "queue_job")).structured_content or {}

    assert data["currentState"] == "TOQUEUE"
    assert "wait_for_jobs" in data["next_step"]


async def test_queue_job_refused_lists_the_possible_causes(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.post(f"{CP}/jobs/31/queue").respond(409, text="Job 31 is not approved")

    result = await call(agent, "queue_job")

    assert result.is_error
    assert "Job 31 is not approved" in text_of(result)
    assert "a human must approve it" in text_of(result)
    assert "retry_job_preparation" in text_of(result)


async def test_retry_job_preparation(agent: Agent, core_api: respx.MockRouter) -> None:
    core_api.post(f"{CP}/jobs/31/retry-preparation").respond(202)

    data = (await call(agent, "retry_job_preparation")).structured_content or {}

    assert data == {
        "jobId": 31,
        "accepted": True,
        "next_step": data["next_step"],
    }
    assert "configFileStatus is READY" in data["next_step"]


async def test_retry_job_preparation_refused_says_what_can_be_retried(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.post(f"{CP}/jobs/31/retry-preparation").respond(409, text="Job is not a draft")

    result = await call(agent, "retry_job_preparation")

    assert result.is_error
    assert "Only a DRAFT job" in text_of(result)


async def test_set_job_ai_result_saves_the_text(agent: Agent, core_api: respx.MockRouter) -> None:
    route = core_api.post(f"{CP}/jobs/31/ai-result").respond(204)

    data = (
        await call(agent, "set_job_ai_result", ai_result="No difference; no finding.")
    ).structured_content

    assert data == {"jobId": 31, "saved": True}
    assert json.loads(route.calls.last.request.content) == {
        "aiResult": "No difference; no finding."
    }


async def test_set_job_ai_result_before_the_job_ended_says_to_wait(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.post(f"{CP}/jobs/31/ai-result").respond(409, text="Job has not ended")

    result = await call(agent, "set_job_ai_result")

    assert result.is_error
    assert "use wait_for_jobs" in text_of(result)


async def test_set_job_ai_result_over_the_limit_shows_the_core_message(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.post(f"{CP}/jobs/31/ai-result").respond(
        400, text="aiResult must be at most 5000 characters"
    )

    result = await call(agent, "set_job_ai_result", ai_result="x" * 6000)

    assert result.is_error
    assert "at most 5000 characters" in text_of(result)


async def test_cancel_job_says_what_happened(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.post(f"{CP}/jobs/31/cancel").respond(204)
    core_api.get(f"{CP}/jobs/31").respond(
        200, json=job(load_fixture, currentState="RUNNING", requestedState="CANCELED")
    )

    data = (await call(agent, "cancel_job")).structured_content or {}

    assert data["requestedState"] == "CANCELED"
    assert "worker carries it out" in data["next_step"]


async def test_cancel_job_of_a_draft_explains_what_can_be_canceled(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.post(f"{CP}/jobs/31/cancel").respond(409, text="A draft cannot be canceled")

    result = await call(agent, "cancel_job")

    assert result.is_error
    assert "A draft cannot be canceled" in text_of(result)
    assert "TOQUEUE, QUEUED, RUNNING or PAUSED" in text_of(result)


# ---------------------------------------------------------------------------- wait_for_jobs


def quick_agent(**changes: Any) -> Agent:
    settings = Settings(  # type: ignore[call-arg]
        _env_file=None,
        core_url=BASE,
        get_retries=0,
        wait_poll_seconds=0.01,
        wait_default_seconds=0.05,
        wait_max_seconds=0.1,
        wait_max_job_ids=3,
        **changes,
    )
    return Agent(settings)


async def test_wait_for_jobs_returns_when_a_job_ended(
    core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/jobs/31").respond(
        200, json=job(load_fixture, currentState="FINISHED", terminal=True, waitingForAction=False)
    )

    data = (await call(quick_agent(), "wait_for_jobs")).structured_content or {}

    assert data["terminalJobIds"] == [31]
    assert data["timedOut"] is False
    assert data["jobs"][0]["currentState"] == "FINISHED"
    assert "get_job_result" in data["next_steps"][0]


async def test_wait_for_jobs_times_out_and_says_to_call_again(
    core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/jobs/31").respond(
        200, json=job(load_fixture, currentState="RUNNING", waitingForAction=False)
    )

    data = (
        await call(quick_agent(), "wait_for_jobs", timeout_seconds=0.03)
    ).structured_content or {}

    assert data["timedOut"] is True
    assert "call wait_for_jobs again" in data["next_steps"][-1]


async def test_the_wait_never_exceeds_the_limit_of_the_server(
    core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    import time

    core_api.get(f"{CP}/jobs/31").respond(
        200, json=job(load_fixture, currentState="RUNNING", waitingForAction=False)
    )
    started = time.monotonic()

    data = (
        await call(quick_agent(), "wait_for_jobs", timeout_seconds=3600)
    ).structured_content or {}

    assert data["timedOut"] is True
    assert time.monotonic() - started < 2


async def test_wait_for_jobs_returns_at_once_for_a_job_waiting_for_a_human(
    core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/jobs/31").respond(200, json=job(load_fixture))

    data = (await call(quick_agent(), "wait_for_jobs")).structured_content or {}

    assert data["waitingForActionJobIds"] == [31]
    assert data["timedOut"] is False


async def test_duplicate_ids_are_waited_for_once(
    core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core_api.get(f"{CP}/jobs/31").respond(
        200, json=job(load_fixture, currentState="FINISHED", terminal=True)
    )

    data = (
        await call(quick_agent(), "wait_for_jobs", job_ids=[31, 31, 31, 31])
    ).structured_content or {}

    assert len(data["jobs"]) == 1
    assert route.call_count == 1


async def test_too_many_jobs_at_once_is_refused(core_api: respx.MockRouter) -> None:
    result = await call(quick_agent(), "wait_for_jobs", job_ids=[1, 2, 3, 4])

    assert result.is_error
    assert "at most 3 at a time" in text_of(result)
    assert not [c for c in core_api.calls if "/jobs/" in c.request.url.path]


async def test_an_empty_list_of_jobs_is_refused_by_the_schema(agent: Agent) -> None:
    result = await call(agent, "wait_for_jobs", job_ids=[])

    assert result.is_error


async def test_a_job_outside_the_context_fails_the_wait(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/jobs/31").respond(404, text="Job not found")

    result = await call(agent, "wait_for_jobs")

    assert result.is_error
    assert "Not found, or not part of this context" in text_of(result)


# ---------------------------------------------------------------------------- every tool


@pytest.mark.parametrize("tool", sorted(TOOLS))
async def test_a_bad_uuid_never_reaches_the_core(
    agent: Agent, core_api: respx.MockRouter, tool: str
) -> None:
    result = await agent.call_tool(tool, {"context_uuid": "nope"} | ARGUMENTS[tool])

    assert result.is_error
    assert "context_uuid must be a UUID" in text_of(result)
    assert not [c for c in core_api.calls if c.request.url.path.startswith("/api/agent-contexts")]


@pytest.mark.parametrize("tool", sorted(TOOLS - {"create_job", "list_jobs", "wait_for_jobs"}))
async def test_an_id_that_is_not_a_number_is_refused_by_the_schema(
    agent: Agent, core_api: respx.MockRouter, tool: str
) -> None:
    result = await agent.call_tool(
        tool, {"context_uuid": CTX} | ARGUMENTS[tool] | {"job_id": "abc"}
    )

    assert result.is_error
