"""The two resources: the briefing of a context and a routine, read through MCP."""

import json
from collections.abc import Callable
from typing import Any

import pytest
import respx
from mcp import MCPError

from helpers import Agent

CTX = "6f1c0a52-0000-4000-8000-000000000001"
CP = f"/api/agent-contexts/uuid/{CTX}"


def json_of(result: Any) -> dict[str, Any]:
    assert len(result.contents) == 1
    return json.loads(result.contents[0].text)  # type: ignore[no-any-return]


# ---------------------------------------------------------------------------- the listing


async def test_two_templates_are_offered_and_no_fixed_resource(agent: Agent) -> None:
    templates = (await agent.list_resource_templates()).resource_templates

    assert {t.uri_template for t in templates} == {
        "automata://context/{context_uuid}/briefing",
        "automata://routine/{routine_id}",
    }
    assert {t.mime_type for t in templates} == {"application/json"}
    assert all(t.description for t in templates)


# ---------------------------------------------------------------------------- the briefing


async def test_the_briefing_resource_is_the_briefing_tool_as_json(
    agent: Agent, known_context: respx.MockRouter
) -> None:
    resource = await agent.read_resource(f"automata://context/{CTX}/briefing")
    tool = await agent.call_tool("get_context_briefing", {"context_uuid": CTX})

    assert resource.contents[0].mime_type == "application/json"
    assert json_of(resource) == tool.structured_content
    assert json_of(resource)["context"]["uuid"] == CTX


async def test_the_briefing_is_readable_json_with_the_unicode_kept(
    agent: Agent, known_context: respx.MockRouter
) -> None:
    resource = await agent.read_resource(f"automata://context/{CTX}/briefing")

    text = resource.contents[0].text  # type: ignore[union-attr]
    assert text.startswith("{\n  ")
    assert "\\u" not in text


async def test_a_briefing_with_a_bad_uuid_never_reaches_the_core(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    with pytest.raises(MCPError, match="context_uuid must be a UUID"):
        await agent.read_resource("automata://context/nope/briefing")

    assert not [c for c in core_api.calls if c.request.url.path.startswith("/api/agent-contexts")]


async def test_a_briefing_of_an_unknown_context_says_so(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(url__regex=rf"{CP}.*").respond(404, text="Agent context not found")

    with pytest.raises(MCPError, match="Not found, or not part of this context") as raised:
        await agent.read_resource(f"automata://context/{CTX}/briefing")

    assert "Agent context not found" in raised.value.error.message


async def test_a_core_failure_reading_the_briefing_is_explained(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}").respond(500, text="database down")
    core_api.get(url__regex=rf"{CP}/.*").respond(500, text="database down")

    with pytest.raises(MCPError, match=r"The core failed \(HTTP 500\)"):
        await agent.read_resource(f"automata://context/{CTX}/briefing")


# ---------------------------------------------------------------------------- the routine


async def test_the_routine_resource_is_the_routine_tool_as_json(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get("/api/routines/3").respond(200, json=load_fixture("routine_detailed"))

    resource = await agent.read_resource("automata://routine/3")
    tool = await agent.call_tool("get_routine", {"routine_id": 3})

    assert json_of(resource) == tool.structured_content
    assert json_of(resource)["configSchema"]["properties"]["rawRequestBase64"] == {"type": "string"}
    assert resource.contents[0].mime_type == "application/json"


@pytest.mark.parametrize("value", ["abc", "-1", "1.5", "", "1%2F2"])
async def test_a_routine_id_that_is_not_a_whole_number_is_refused_before_any_call(
    agent: Agent, core_api: respx.MockRouter, value: str
) -> None:
    with pytest.raises(MCPError, match="routine_id must be a whole number"):
        await agent.read_resource(f"automata://routine/{value}")

    assert not [c for c in core_api.calls if c.request.url.path.startswith("/api/routines/")]


async def test_an_unknown_routine_is_not_found(agent: Agent, core_api: respx.MockRouter) -> None:
    core_api.get("/api/routines/99").respond(404, text="Routine not found")

    with pytest.raises(MCPError, match="Routine not found"):
        await agent.read_resource("automata://routine/99")
