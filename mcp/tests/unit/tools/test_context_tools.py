"""The context tools, called the way an agent calls them: through MCP, against a fake core."""

from collections.abc import Callable
from typing import Any

import pytest
import respx
from mcp.types import CallToolResult, Tool

from helpers import Agent

CTX = "6f1c0a52-0000-4000-8000-000000000001"
CP = f"/api/agent-contexts/uuid/{CTX}"
TOOLS = ["get_context_briefing", "get_context", "get_host", "list_targets", "list_tenants"]


async def context_tools(agent: Agent) -> list[Tool]:
    return [tool for tool in (await agent.list_tools()).tools if tool.name in TOOLS]


def text_of(result: CallToolResult) -> str:
    return result.content[0].text  # type: ignore[union-attr]


@pytest.fixture
def known_context(
    core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> respx.MockRouter:
    core_api.get(CP).respond(200, json=load_fixture("agent_context_detailed"))
    core_api.get(f"{CP}/host").respond(200, json=load_fixture("host_detailed"))
    core_api.get(f"{CP}/resolved-requests").respond(200, json=load_fixture("resolved_targets"))
    core_api.get(f"{CP}/tenants").respond(200, json=load_fixture("context_tenants"))
    metadata = load_fixture("request_metadata")
    core_api.get(f"{CP}/requests/7/metadata").respond(200, json=metadata)
    core_api.get(f"{CP}/requests/21/metadata").respond(200, json=metadata | {"requestId": 21})
    core_api.get(f"{CP}/jobs").respond(200, json=load_fixture("job_summary_page"))
    core_api.get(f"{CP}/findings").respond(200, json=load_fixture("finding_summary_page"))
    core_api.get("/api/routines/1").respond(200, json=load_fixture("routine_detailed"))
    core_api.get("/api/routines/2").respond(404, text="Routine not found")
    return core_api


# ---------------------------------------------------------------------------- the tool list


async def test_the_five_context_tools_are_registered(agent: Agent) -> None:
    names = {tool.name for tool in await context_tools(agent)}

    assert names == set(TOOLS)


async def test_every_context_tool_is_read_only_and_closed_world(agent: Agent) -> None:
    for tool in await context_tools(agent):
        assert tool.annotations is not None
        assert tool.annotations.read_only_hint is True
        assert tool.annotations.open_world_hint is False


async def test_every_context_tool_requires_a_context_uuid_and_hides_the_sdk_context(
    agent: Agent,
) -> None:
    for tool in await context_tools(agent):
        assert "context_uuid" in tool.input_schema["required"], tool.name
        assert "ctx" not in tool.input_schema["properties"], tool.name
        assert "UUID" in tool.input_schema["properties"]["context_uuid"]["description"]


async def test_every_context_tool_explains_itself(agent: Agent) -> None:
    for tool in await context_tools(agent):
        assert tool.description
        assert len(tool.description) > 150, tool.name


async def test_list_targets_offers_with_summary_on_by_default(agent: Agent) -> None:
    tool = next(t for t in await context_tools(agent) if t.name == "list_targets")

    assert tool.input_schema["properties"]["with_summary"]["default"] is True
    assert tool.input_schema["required"] == ["context_uuid"]


# ---------------------------------------------------------------------------- results


async def test_get_context_returns_the_configuration_as_the_core_names_it(
    agent: Agent, known_context: respx.MockRouter
) -> None:
    result = await agent.call_tool("get_context", {"context_uuid": CTX})

    assert not result.is_error
    data = result.structured_content
    assert data is not None
    assert data["uuid"] == CTX
    assert data["representativeStrategy"] == "LATEST"
    assert data["allowedRoutineIds"] == [1, 2]
    assert data["items"][1] == {"id": 11, "itemType": "EQUALITY_SET", "equalitySetId": 55}


async def test_get_host_returns_the_rate_limits(
    agent: Agent, known_context: respx.MockRouter
) -> None:
    result = await agent.call_tool("get_host", {"context_uuid": CTX})

    data = result.structured_content
    assert data is not None
    assert (data["hostRateLimit"], data["shortRateLimit"], data["longRateLimit"]) == (50, 20, 10)
    assert data["technologies"] == ["nginx", "spring"]
    assert "notes" not in data


async def test_list_tenants_returns_authentication_and_request_ids(
    agent: Agent, known_context: respx.MockRouter
) -> None:
    result = await agent.call_tool("list_tenants", {"context_uuid": CTX})

    tenants = (result.structured_content or {})["tenants"]
    assert [t["tenantId"] for t in tenants] == [4, 5]
    assert tenants[0]["authData"]["data"]["cookies"] == [{"key": "sid", "value": "s1"}]
    assert tenants[0]["requestIds"] == [7, 21]
    assert "authData" not in tenants[1]


async def test_list_targets_enriches_by_default(
    agent: Agent, known_context: respx.MockRouter
) -> None:
    result = await agent.call_tool("list_targets", {"context_uuid": CTX})

    data = result.structured_content or {}
    assert data["total"] == 2
    assert data["enriched"] == 2
    assert data["targets"][0]["method"] == "POST"
    assert data["targets"][0]["automataJobEligible"] is True
    assert data["targets"][1]["automataJobEligible"] is False
    assert any("not job-eligible" in note for note in data["notes"])


async def test_list_targets_without_summary_makes_no_request_calls(
    agent: Agent, known_context: respx.MockRouter
) -> None:
    result = await agent.call_tool("list_targets", {"context_uuid": CTX, "with_summary": False})

    data = result.structured_content or {}
    assert "enriched" not in data
    assert "method" not in data["targets"][0]
    assert not [c for c in known_context.calls if "/metadata" in c.request.url.path]


async def test_the_briefing_has_every_section_of_the_plan(
    agent: Agent, known_context: respx.MockRouter
) -> None:
    result = await agent.call_tool("get_context_briefing", {"context_uuid": CTX})

    assert not result.is_error
    data = result.structured_content or {}
    assert set(data) == {
        "context",
        "host",
        "targets",
        "tenants",
        "allowedRoutines",
        "recentJobs",
        "recentFindings",
        "notes",
    }
    assert data["recentFindings"]["total"] == 2
    assert [job["id"] for job in data["recentJobs"]] == [31, 30]
    assert data["context"]["defaults"] == {
        "duration": "SHORT",
        "verbosity": "SUMMARIZED",
        "priority": 2,
    }
    assert data["allowedRoutines"][0]["key"] == "fuzz"
    assert "error" in data["allowedRoutines"][1]
    assert data["notes"]


async def test_results_are_also_given_as_json_text(
    agent: Agent, known_context: respx.MockRouter
) -> None:
    result = await agent.call_tool("get_host", {"context_uuid": CTX})

    assert '"hostRateLimit": 50' in text_of(result)


# ---------------------------------------------------------------------------- errors


@pytest.mark.parametrize("tool", TOOLS)
async def test_a_bad_uuid_is_an_error_the_agent_can_fix_and_reaches_no_route(
    agent: Agent, core_api: respx.MockRouter, tool: str
) -> None:
    result = await agent.call_tool(tool, {"context_uuid": "not-a-uuid"})

    assert result.is_error
    assert "context_uuid must be a UUID" in text_of(result)
    assert not [c for c in core_api.calls if c.request.url.path.startswith("/api/agent-contexts")]


@pytest.mark.parametrize("tool", TOOLS)
async def test_a_missing_uuid_is_refused_by_the_schema(agent: Agent, tool: str) -> None:
    result = await agent.call_tool(tool, {})

    assert result.is_error
    assert "context_uuid" in text_of(result)


@pytest.mark.parametrize("tool", TOOLS)
async def test_an_unknown_context_gives_the_core_message_with_guidance(
    agent: Agent, core_api: respx.MockRouter, tool: str
) -> None:
    core_api.get(url__regex=rf"{CP}.*").respond(404, text="Agent context not found")

    result = await agent.call_tool(tool, {"context_uuid": CTX})

    assert result.is_error
    assert "Not found, or not part of this context" in text_of(result)
    assert "Agent context not found" in text_of(result)


async def test_a_core_that_is_down_is_reported_as_unreachable(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    import httpx

    core_api.get(f"{CP}/host").mock(side_effect=httpx.ConnectError("refused"))

    result = await agent.call_tool("get_host", {"context_uuid": CTX})

    assert result.is_error
    assert "Cannot reach the core" in text_of(result)
