"""The finding tools, called through MCP against a fake core."""

import json
from collections.abc import Callable
from typing import Any

import pytest
import respx
from mcp.types import CallToolResult

from helpers import Agent

CTX = "6f1c0a52-0000-4000-8000-000000000001"
CP = f"/api/agent-contexts/uuid/{CTX}"
TOOLS = {"create_finding", "get_finding", "list_findings", "update_finding"}
ARGUMENTS: dict[str, dict[str, Any]] = {
    "create_finding": {
        "request_id": 7,
        "vulnerability_id": 12,
        "severity": 7.5,
        "confidence": 8.0,
        "status": "SUGGESTED",
        "evidence": "returned tenant B's profile",
    },
    "get_finding": {"finding_id": 5},
    "list_findings": {},
    "update_finding": {"finding_id": 5, "severity": 6.0},
}


def text_of(result: CallToolResult) -> str:
    return result.content[0].text  # type: ignore[union-attr]


async def call(agent: Agent, tool: str, **extra: Any) -> CallToolResult:
    return await agent.call_tool(tool, {"context_uuid": CTX} | ARGUMENTS[tool] | extra)


# ---------------------------------------------------------------------------- the tool list


async def test_the_four_finding_tools_are_registered_with_the_right_annotations(
    agent: Agent,
) -> None:
    tools = {t.name: t for t in (await agent.list_tools()).tools if t.name in TOOLS}

    assert set(tools) == TOOLS
    flags = {
        name: (a.read_only_hint, a.destructive_hint, a.idempotent_hint, a.open_world_hint)
        for name, tool in tools.items()
        if (a := tool.annotations)
    }
    assert flags == {
        "create_finding": (False, False, False, False),
        "get_finding": (True, None, None, False),
        "list_findings": (True, None, None, False),
        "update_finding": (False, False, True, False),
    }
    for name, tool in tools.items():
        assert "context_uuid" in tool.input_schema["required"], name
        assert "ctx" not in tool.input_schema["properties"], name
        assert len(tool.description or "") > 150, name


async def test_create_finding_schema(agent: Agent) -> None:
    tool = next(t for t in (await agent.list_tools()).tools if t.name == "create_finding")

    assert set(tool.input_schema["required"]) == {
        "context_uuid",
        "request_id",
        "vulnerability_id",
        "severity",
        "confidence",
        "status",
        "evidence",
    }
    assert tool.input_schema["properties"]["status"]["enum"] == ["SUGGESTED", "CONFIRMED"]
    assert "CONFIRMED needs `poc_reproduction_steps`" in (tool.description or "")


async def test_list_findings_filters_are_choices_of_the_core(agent: Agent) -> None:
    tool = next(t for t in (await agent.list_tools()).tools if t.name == "list_findings")
    properties = tool.input_schema["properties"]

    assert "TRUE_POSITIVE" in json.dumps(properties["human_verdict"])
    assert "USER" in json.dumps(properties["created_by"])
    assert tool.input_schema["required"] == ["context_uuid"]


# ---------------------------------------------------------------------------- create_finding


async def test_create_finding_posts_and_returns_the_id(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    post = core_api.post(f"{CP}/findings").respond(201, headers={"Location": f"{CP}/findings/5"})

    result = await call(
        agent,
        "create_finding",
        status="CONFIRMED",
        poc_reproduction_steps="1. Log in as A. 2. GET /api/users/2.",
        job_id=31,
        ai_description="BOLA",
    )

    data = result.structured_content or {}
    assert data["findingId"] == 5
    assert "A human will review" in data["next_step"]
    sent = json.loads(post.calls.last.request.content)
    assert sent["status"] == "CONFIRMED"
    assert sent["jobId"] == 31
    assert sent["pocReproductionSteps"].startswith("1. Log in")


async def test_a_finding_on_an_unreadable_request_or_foreign_job_gets_a_hint(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.post(f"{CP}/findings").respond(404, text="Request not found in the agent context")

    result = await call(agent, "create_finding", job_id=99)

    assert result.is_error
    assert "Request not found in the agent context" in text_of(result)
    assert "not readable in this context, or the job is not one of your jobs" in text_of(result)


async def test_the_rules_of_the_core_are_shown(agent: Agent, core_api: respx.MockRouter) -> None:
    core_api.post(f"{CP}/findings").respond(
        400, text="pocReproductionSteps is required when CONFIRMED"
    )

    result = await call(agent, "create_finding", status="CONFIRMED")

    assert result.is_error
    assert "pocReproductionSteps is required when CONFIRMED" in text_of(result)


@pytest.mark.parametrize(
    "bad", [{"status": "LIKELY"}, {"severity": "high"}, {"request_id": "seven"}]
)
async def test_arguments_of_the_wrong_kind_are_refused_before_any_call(
    agent: Agent, core_api: respx.MockRouter, bad: dict[str, Any]
) -> None:
    result = await call(agent, "create_finding", **bad)

    assert result.is_error
    assert not [c for c in core_api.calls if c.request.method == "POST"]


# ---------------------------------------------------------------------------- reads


async def test_get_finding_shows_the_human_review(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/findings/5").respond(200, json=load_fixture("finding_detailed"))

    data = (await call(agent, "get_finding")).structured_content or {}

    assert data["humanVerdict"] == "TRUE_POSITIVE"
    assert data["humanComment"] == "Reproduced"
    assert data["origin"] == "THIS_CONTEXT"
    assert data["pocReproductionSteps"].startswith("1. Log in")
    assert data["reviewedAt"] == "2026-10-23T08:00:00Z"


async def test_get_finding_about_another_context_is_not_found_with_a_hint(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/findings/5").respond(404, text="Finding not found")

    result = await call(agent, "get_finding")

    assert result.is_error
    assert "Finding not found" in text_of(result)
    assert "not about a request of this context" in text_of(result)


async def test_list_findings_returns_a_page_with_origins(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core_api.get(f"{CP}/findings").respond(200, json=load_fixture("finding_summary_page"))

    data = (await call(agent, "list_findings")).structured_content or {}

    assert [(f["id"], f["origin"]) for f in data["findings"]] == [(5, "THIS_CONTEXT"), (4, "USER")]
    assert data["findings"][0]["humanVerdict"] == "TRUE_POSITIVE"
    assert "humanVerdict" not in data["findings"][1]
    assert dict(route.calls.last.request.url.params) == {"page": "0", "size": "20"}


async def test_list_findings_sends_its_filters_as_the_core_names_them(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core_api.get(f"{CP}/findings").respond(200, json=load_fixture("finding_summary_page"))

    await call(
        agent,
        "list_findings",
        request_id=7,
        job_id=31,
        status="CONFIRMED",
        human_verdict="FALSE_POSITIVE",
        created_by="AI",
        page=2,
        size=500,
    )

    assert dict(route.calls.last.request.url.params) == {
        "requestId": "7",
        "jobId": "31",
        "status": "CONFIRMED",
        "humanVerdict": "FALSE_POSITIVE",
        "createdBy": "AI",
        "page": "2",
        "size": "100",
    }


@pytest.mark.parametrize(
    "bad", [{"status": "LIKELY"}, {"human_verdict": "MAYBE"}, {"created_by": "ROBOT"}]
)
async def test_a_filter_the_core_does_not_have_is_refused(
    agent: Agent, core_api: respx.MockRouter, bad: dict[str, Any]
) -> None:
    result = await call(agent, "list_findings", **bad)

    assert result.is_error
    assert not [c for c in core_api.calls if c.request.url.path.endswith("/findings")]


# ---------------------------------------------------------------------------- update_finding


async def test_update_finding_changes_only_the_given_fields_and_returns_the_result(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    patch = core_api.patch(f"{CP}/findings/5").respond(204)
    core_api.get(f"{CP}/findings/5").respond(
        200, json=load_fixture("finding_detailed") | {"severity": 6.0}
    )

    result = await call(agent, "update_finding", status="CONFIRMED")

    assert json.loads(patch.calls.last.request.content) == {"severity": 6.0, "status": "CONFIRMED"}
    data = result.structured_content or {}
    assert data["id"] == 5
    assert data["severity"] == 6.0


async def test_update_finding_without_any_field_is_an_error_and_calls_nothing(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    result = await agent.call_tool("update_finding", {"context_uuid": CTX, "finding_id": 5})

    assert result.is_error
    assert "Give at least one field to change" in text_of(result)
    assert not [c for c in core_api.calls if c.request.method == "PATCH"]


async def test_a_finding_that_cannot_be_edited_says_which_can(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.patch(f"{CP}/findings/5").respond(409, text="Finding was reviewed by a human")

    result = await call(agent, "update_finding")

    assert result.is_error
    assert "Finding was reviewed by a human" in text_of(result)
    assert "Only a finding you created in this context, and not yet reviewed by a human" in text_of(
        result
    )


async def test_updating_a_finding_that_is_not_visible_is_not_found(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.patch(f"{CP}/findings/5").respond(404, text="Finding not found")

    result = await call(agent, "update_finding")

    assert result.is_error
    assert "not about a request of this context" in text_of(result)


# ---------------------------------------------------------------------------- every tool


@pytest.mark.parametrize("tool", sorted(TOOLS))
async def test_a_bad_uuid_never_reaches_the_core(
    agent: Agent, core_api: respx.MockRouter, tool: str
) -> None:
    result = await agent.call_tool(tool, {"context_uuid": "nope"} | ARGUMENTS[tool])

    assert result.is_error
    assert "context_uuid must be a UUID" in text_of(result)
    assert not [c for c in core_api.calls if c.request.url.path.startswith("/api/agent-contexts")]
