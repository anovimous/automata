"""create_wordlist, called through MCP against a fake core."""

import json
from typing import Any

import pytest
import respx
from mcp.types import CallToolResult

from helpers import Agent, multipart_parts


def text_of(result: CallToolResult) -> str:
    return result.content[0].text  # type: ignore[union-attr]


async def create(agent: Agent, **extra: Any) -> CallToolResult:
    return await agent.call_tool(
        "create_wordlist",
        {
            "name": "ids.txt",
            "content": "1\n2\n",
            "small_description": "Sequential ids",
            "payload_content_description": "Decimal ids, one per line",
        }
        | extra,
    )


async def test_the_tool_is_a_creating_write_that_needs_no_context(agent: Agent) -> None:
    tool = next(t for t in (await agent.list_tools()).tools if t.name == "create_wordlist")

    assert set(tool.input_schema["required"]) == {
        "name",
        "content",
        "small_description",
        "payload_content_description",
    }
    assert "ctx" not in tool.input_schema["properties"]
    assert "context_uuid" not in tool.input_schema["properties"]
    annotations = tool.annotations
    assert annotations is not None
    assert (
        annotations.read_only_hint,
        annotations.destructive_hint,
        annotations.idempotent_hint,
    ) == (
        False,
        False,
        False,
    )
    assert annotations.open_world_hint is False


async def test_the_description_tells_the_agent_what_it_must_know(agent: Agent) -> None:
    tool = next(t for t in (await agent.list_tools()).tools if t.name == "create_wordlist")
    description = tool.description or ""

    for phrase in (
        "can not be changed afterwards",
        "small_description",
        "payload_content_description",
        "500",
        "2000",
        "512 MB",
        "already exists",
        "4 MB",
    ):
        assert phrase in description


async def test_a_wordlist_is_created_and_its_id_returned(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    route = core_api.post("/api/wordlists").respond(201, headers={"Location": "/api/wordlists/9"})

    result = await create(agent, vulnerability_ids=[12])

    assert (result.structured_content or {})["wordlistId"] == 9
    parts = multipart_parts(route.calls.last.request)
    assert parts["file"] == ("ids.txt", "text/plain", b"1\n2\n")
    assert json.loads(parts["metadata"][2]) == {
        "name": "ids.txt",
        "smallDescription": "Sequential ids",
        "payloadContentDescription": "Decimal ids, one per line",
        "vulnIds": [12],
    }


async def test_a_duplicate_tells_the_agent_to_use_the_existing_wordlist(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.post("/api/wordlists").respond(409, text="Wordlist already exists with id 3")

    result = await create(agent)

    assert result.is_error
    assert "Wordlist already exists with id 3" in text_of(result)
    assert "use it instead of creating another" in text_of(result)


async def test_the_rules_of_the_core_are_shown(agent: Agent, core_api: respx.MockRouter) -> None:
    core_api.post("/api/wordlists").respond(400, text="The wordlist file is empty")

    result = await create(agent, content="")

    assert result.is_error
    assert "The wordlist file is empty" in text_of(result)


async def test_a_file_that_is_too_large_is_explained(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.post("/api/wordlists").respond(413, text="The uploaded file is too large")

    result = await create(agent)

    assert result.is_error
    assert "512 MB" in text_of(result)


@pytest.mark.parametrize(
    "arguments",
    [
        {"content": 5},
        {"name": None},
        {"small_description": None},
        {"payload_content_description": None},
        {"vulnerability_ids": ["a"]},
        {"vulnerability_ids": 7},
    ],
)
async def test_arguments_of_the_wrong_kind_are_refused_before_any_call(
    agent: Agent, core_api: respx.MockRouter, arguments: dict[str, Any]
) -> None:
    result = await create(agent, **arguments)

    assert result.is_error
    assert not [c for c in core_api.calls if c.request.method == "POST"]


async def test_the_core_that_is_down_is_reported_as_possibly_applied(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    import httpx

    core_api.post("/api/wordlists").mock(side_effect=httpx.ReadTimeout("slow"))

    result = await create(agent)

    assert result.is_error
    assert "may or may not have been applied" in text_of(result)
