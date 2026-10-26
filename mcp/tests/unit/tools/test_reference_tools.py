"""The reference tools (routines, vulnerabilities, wordlists), called through MCP."""

from collections.abc import Callable
from typing import Any

import pytest
import respx
from mcp.types import CallToolResult

from helpers import Agent

TOOLS = [
    "list_routines",
    "get_routine",
    "list_vulnerabilities",
    "get_vulnerability",
    "list_wordlists",
    "get_wordlist",
]
LISTS = {
    "list_routines": ("/api/routines", "routines", "routine_summary_page"),
    "list_vulnerabilities": (
        "/api/vulnerabilities",
        "vulnerabilities",
        "vulnerability_summary_page",
    ),
    "list_wordlists": ("/api/wordlists", "wordlists", "wordlist_summary_page"),
}
DETAILS = {
    "get_routine": ("routine_id", "/api/routines/3", "routine_detailed"),
    "get_vulnerability": ("vulnerability_id", "/api/vulnerabilities/3", "vulnerability_detailed"),
    "get_wordlist": ("wordlist_id", "/api/wordlists/3", "wordlist_detailed"),
}


def text_of(result: CallToolResult) -> str:
    return result.content[0].text  # type: ignore[union-attr]


# ---------------------------------------------------------------------------- the tool list


async def test_the_six_reference_tools_are_registered(agent: Agent) -> None:
    names = {tool.name for tool in (await agent.list_tools()).tools}

    assert set(TOOLS) <= names


async def test_reference_tools_are_read_only_and_need_no_context(agent: Agent) -> None:
    tools = {t.name: t for t in (await agent.list_tools()).tools if t.name in TOOLS}

    for name, tool in tools.items():
        assert tool.annotations is not None
        assert tool.annotations.read_only_hint is True
        assert tool.annotations.open_world_hint is False
        assert "context_uuid" not in tool.input_schema["properties"], name
        assert "ctx" not in tool.input_schema["properties"], name
        assert len(tool.description or "") > 150, name


async def test_list_tools_have_optional_filters_and_paging(agent: Agent) -> None:
    tools = {t.name: t for t in (await agent.list_tools()).tools}

    for name in LISTS:
        schema = tools[name].input_schema
        assert schema.get("required", []) == [], name
        assert schema["properties"]["page"]["default"] == 0
        assert schema["properties"]["size"]["default"] == 30
        assert schema["properties"]["query"]["default"] == ""


async def test_get_tools_require_their_id(agent: Agent) -> None:
    tools = {t.name: t for t in (await agent.list_tools()).tools}

    for name, (argument, _, _) in DETAILS.items():
        assert tools[name].input_schema["required"] == [argument]


# ---------------------------------------------------------------------------- lists


@pytest.mark.parametrize("tool", list(LISTS))
async def test_a_list_returns_its_items_and_the_paging_fields(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any], tool: str
) -> None:
    path, key, fixture = LISTS[tool]
    core_api.get(path).respond(200, json=load_fixture(fixture))

    result = await agent.call_tool(tool, {})

    assert not result.is_error
    data = result.structured_content or {}
    assert data[key]
    assert (data["page"], data["size"], data["last"]) == (0, 30, True)
    assert "nextPage" not in data


@pytest.mark.parametrize("tool", list(LISTS))
async def test_a_list_sends_the_default_paging_and_an_empty_query(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any], tool: str
) -> None:
    path, _, fixture = LISTS[tool]
    route = core_api.get(path).respond(200, json=load_fixture(fixture))

    await agent.call_tool(tool, {})

    assert dict(route.calls.last.request.url.params) == {"query": "", "page": "0", "size": "30"}


@pytest.mark.parametrize(
    ("tool", "argument", "param"),
    [
        ("list_routines", "vulnerability_id", "vulnId"),
        ("list_vulnerabilities", "parent_id", "parentVulnId"),
        ("list_wordlists", "vulnerability_id", "vulnId"),
    ],
)
async def test_a_list_passes_its_filters_to_the_core(
    agent: Agent,
    core_api: respx.MockRouter,
    load_fixture: Callable[[str], Any],
    tool: str,
    argument: str,
    param: str,
) -> None:
    path, _, fixture = LISTS[tool]
    route = core_api.get(path).respond(200, json=load_fixture(fixture))

    await agent.call_tool(tool, {"query": "ids", argument: 12, "page": 2, "size": 10})

    assert dict(route.calls.last.request.url.params) == {
        "query": "ids",
        param: "12",
        "page": "2",
        "size": "10",
    }


async def test_a_page_size_over_100_is_cut_to_100(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core_api.get("/api/wordlists").respond(200, json=load_fixture("wordlist_summary_page"))

    await agent.call_tool("list_wordlists", {"size": 500, "page": -3})

    params = dict(route.calls.last.request.url.params)
    assert (params["page"], params["size"]) == ("0", "100")


async def test_a_list_with_more_pages_says_which_comes_next(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    page = load_fixture("routine_summary_page") | {"last": False, "totalPages": 3, "page": 1}
    core_api.get("/api/routines").respond(200, json=page)

    data = (await agent.call_tool("list_routines", {"page": 1})).structured_content or {}

    assert data["nextPage"] == 2
    assert data["totalPages"] == 3


async def test_routine_summaries_show_what_decides_the_choice(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get("/api/routines").respond(200, json=load_fixture("routine_summary_page"))

    routine = ((await agent.call_tool("list_routines", {})).structured_content or {})["routines"][0]

    assert routine["key"] == "fuzz"
    assert routine["wordlistRequired"] is True
    assert routine["allowedTargets"] == ["SINGLE_REQUEST", "MULTIPLE_REQUESTS"]


# ---------------------------------------------------------------------------- details


@pytest.mark.parametrize("tool", list(DETAILS))
async def test_a_get_tool_reads_the_item_by_id(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any], tool: str
) -> None:
    argument, path, fixture = DETAILS[tool]
    core_api.get(path).respond(200, json=load_fixture(fixture))

    result = await agent.call_tool(tool, {argument: 3})

    assert not result.is_error
    assert (result.structured_content or {})["id"] is not None


async def test_get_routine_gives_the_config_schema_and_the_flags(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get("/api/routines/3").respond(200, json=load_fixture("routine_detailed"))

    data = (await agent.call_tool("get_routine", {"routine_id": 3})).structured_content or {}

    assert data["configSchema"]["properties"]["rawRequestBase64"] == {"type": "string"}
    assert data["wordlistRequired"] is True
    assert data["isAvailableAtConsumer"] is True
    assert data["resultContentType"] == "JSON"
    assert "vulnerabilityId" not in data


async def test_get_wordlist_gives_the_hash_and_not_the_content(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get("/api/wordlists/3").respond(200, json=load_fixture("wordlist_detailed"))

    data = (await agent.call_tool("get_wordlist", {"wordlist_id": 3})).structured_content or {}

    assert data["sha256"].startswith("9f86d081")
    assert data["numberOfLines"] == 1000
    assert not [c for c in core_api.calls if c.request.url.path.endswith("/content")]


async def test_get_vulnerability_gives_the_parent(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get("/api/vulnerabilities/3").respond(200, json=load_fixture("vulnerability_detailed"))

    data = (await agent.call_tool("get_vulnerability", {"vulnerability_id": 3})).structured_content

    assert data == {"id": 12, "name": "BOLA", "parentId": 11}


# ---------------------------------------------------------------------------- errors


@pytest.mark.parametrize("tool", list(DETAILS))
async def test_an_unknown_id_gives_the_core_message_with_guidance(
    agent: Agent, core_api: respx.MockRouter, tool: str
) -> None:
    argument, path, _ = DETAILS[tool]
    core_api.get(path).respond(404, text="Entity not found")

    result = await agent.call_tool(tool, {argument: 3})

    assert result.is_error
    assert "Not found, or not part of this context" in text_of(result)
    assert "Entity not found" in text_of(result)


@pytest.mark.parametrize("tool", list(DETAILS))
async def test_an_id_that_is_not_a_number_is_refused_by_the_schema(
    agent: Agent, core_api: respx.MockRouter, tool: str
) -> None:
    argument = DETAILS[tool][0]

    result = await agent.call_tool(tool, {argument: "abc"})

    assert result.is_error
    assert not [c for c in core_api.calls if c.request.url.path != "/api/routines"]


async def test_a_core_failure_on_a_list_is_reported(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get("/api/wordlists").respond(500, text="database down")

    result = await agent.call_tool("list_wordlists", {})

    assert result.is_error
    assert "The core failed (HTTP 500)" in text_of(result)
