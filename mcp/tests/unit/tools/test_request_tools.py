"""The request tools, called through MCP against a fake core."""

import base64
from collections.abc import Callable
from typing import Any

import pytest
import respx
from mcp.types import CallToolResult

from helpers import Agent

CTX = "6f1c0a52-0000-4000-8000-000000000001"
CP = f"/api/agent-contexts/uuid/{CTX}"
TOOLS = ["list_context_requests", "get_request", "get_request_raw", "get_request_body"]
HEAD = "POST /api/users/42 HTTP/1.1\r\nHost: api.example.com\r\n\r\n"


def text_of(result: CallToolResult) -> str:
    return result.content[0].text  # type: ignore[union-attr]


def b64(text: str) -> str:
    return base64.b64encode(text.encode()).decode()


def body_route(
    core: respx.MockRouter, content: bytes, content_type: str = "application/json"
) -> respx.Route:
    return core.get(f"{CP}/requests/7/body/stream").respond(
        200, content=content, headers={"Content-Type": content_type}
    )


# ---------------------------------------------------------------------------- the tool list


async def test_the_four_request_tools_are_read_only_and_take_a_context(agent: Agent) -> None:
    tools = {t.name: t for t in (await agent.list_tools()).tools if t.name in TOOLS}

    assert set(tools) == set(TOOLS)
    for name, tool in tools.items():
        assert tool.annotations is not None
        assert tool.annotations.read_only_hint is True
        assert "context_uuid" in tool.input_schema["required"], name
        assert "ctx" not in tool.input_schema["properties"], name
        assert len(tool.description or "") > 150, name
    for name in ("get_request", "get_request_raw", "get_request_body"):
        assert "request_id" in tools[name].input_schema["required"]


async def test_body_arguments_have_limits_in_their_schema(agent: Agent) -> None:
    tools = {t.name: t for t in (await agent.list_tools()).tools}
    body_tool = tools["get_request_body"].input_schema["properties"]

    assert body_tool["offset"]["minimum"] == 0
    assert body_tool["offset"]["default"] == 0
    assert any(option.get("minimum") == 1 for option in body_tool["max_bytes"]["anyOf"])
    assert tools["get_request"].input_schema["properties"]["include_body"]["default"] is True


# ---------------------------------------------------------------------------- list_context_requests


async def test_list_context_requests_returns_a_page_of_requests(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core_api.get(f"{CP}/requests").respond(200, json=load_fixture("context_requests_page"))

    result = await agent.call_tool("list_context_requests", {"context_uuid": CTX})

    data = result.structured_content or {}
    assert [r["requestId"] for r in data["requests"]] == [21, 19]
    assert data["requests"][1]["resolvedTarget"] is False
    assert data["last"] is True
    assert dict(route.calls.last.request.url.params) == {"page": "0", "size": "20"}


async def test_list_context_requests_filters_by_equality_set_and_cuts_the_size(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core_api.get(f"{CP}/requests").respond(200, json=load_fixture("context_requests_page"))

    await agent.call_tool(
        "list_context_requests",
        {"context_uuid": CTX, "equality_set_id": 55, "page": 1, "size": 500},
    )

    assert dict(route.calls.last.request.url.params) == {
        "equalitySetId": "55",
        "page": "1",
        "size": "100",
    }


async def test_an_unknown_equality_set_gets_a_hint(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/requests").respond(404, text="Equality set not found in the context")

    result = await agent.call_tool(
        "list_context_requests", {"context_uuid": CTX, "equality_set_id": 9}
    )

    assert result.is_error
    assert "Equality set not found in the context" in text_of(result)
    assert "equalitySetId of an equality set item" in text_of(result)


async def test_an_unknown_context_gets_no_equality_set_hint(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/requests").respond(404, text="Agent context not found")

    result = await agent.call_tool("list_context_requests", {"context_uuid": CTX})

    assert result.is_error
    assert "equalitySetId" not in text_of(result)


# ---------------------------------------------------------------------------- get_request


async def test_get_request_returns_the_request_and_the_text_of_its_body(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/requests/7/metadata").respond(200, json=load_fixture("request_metadata"))
    body_route(core_api, b'{"name": "alice"}')

    result = await agent.call_tool("get_request", {"context_uuid": CTX, "request_id": 7})

    data = result.structured_content or {}
    assert data["line"]["target"] == "/api/users/42?expand=items"
    assert data["headers"][1] == {"name": "Content-Type", "value": "application/json"}
    assert data["body"]["storage"] == "INLINE"
    assert data["body"]["mimeFamily"] == "JSON"
    assert data["body"]["text"] == '{"name": "alice"}'
    assert data["body"]["truncated"] is False
    assert data["tenantId"] == 4


async def test_get_request_without_body_makes_no_body_call(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/requests/7/metadata").respond(200, json=load_fixture("request_metadata"))
    route = body_route(core_api, b"not read")

    result = await agent.call_tool(
        "get_request", {"context_uuid": CTX, "request_id": 7, "include_body": False}
    )

    assert "text" not in (result.structured_content or {})["body"]
    assert route.call_count == 0


async def test_get_request_with_no_stored_body_says_so(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    metadata = load_fixture("request_metadata")
    metadata["body"] = {"storage": "NONE", "sizeBytes": 0}
    core_api.get(f"{CP}/requests/7/metadata").respond(200, json=metadata)
    route = body_route(core_api, b"")

    data = (
        await agent.call_tool("get_request", {"context_uuid": CTX, "request_id": 7})
    ).structured_content

    assert (data or {})["body"] == {"storage": "NONE", "sizeBytes": 0, "hasBody": False}
    assert route.call_count == 0


async def test_get_request_describes_a_binary_body_without_fetching_it(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    metadata = load_fixture("request_metadata")
    metadata["body"] = {
        "storage": "FILE",
        "contentType": "image/png",
        "mimeFamily": "IMAGE",
        "sizeBytes": 2048,
    }
    core_api.get(f"{CP}/requests/7/metadata").respond(200, json=metadata)
    route = body_route(core_api, b"\x89PNG", "image/png")

    data = (
        await agent.call_tool("get_request", {"context_uuid": CTX, "request_id": 7})
    ).structured_content

    assert (data or {})["body"]["binary"] is True
    assert (data or {})["body"]["sizeBytes"] == 2048
    assert "text" not in (data or {})["body"]
    assert route.call_count == 0


async def test_get_request_cuts_the_body_at_body_max_bytes_and_says_how_to_go_on(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/requests/7/metadata").respond(200, json=load_fixture("request_metadata"))
    body_route(core_api, b'{"name": "alice"}')

    data = (
        await agent.call_tool(
            "get_request", {"context_uuid": CTX, "request_id": 7, "body_max_bytes": 5}
        )
    ).structured_content or {}

    assert data["body"]["text"] == '{"nam'
    assert data["body"]["truncated"] is True
    assert data["body"]["nextOffset"] == 5
    assert data["body"]["totalBytes"] == 17


async def test_get_request_of_a_request_outside_the_context_is_not_found(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/requests/99/metadata").respond(
        404, text="Request not found in the agent context"
    )

    result = await agent.call_tool("get_request", {"context_uuid": CTX, "request_id": 99})

    assert result.is_error
    assert "Not found, or not part of this context" in text_of(result)
    assert "Request not found in the agent context" in text_of(result)


# ---------------------------------------------------------------------------- get_request_raw


async def test_get_request_raw_gives_the_exact_message(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    raw = {
        "rawHeadBase64": b64(HEAD),
        "rawBodyBase64": b64('{"u":"a"}'),
        "bodyStoredAsFile": False,
        "responseId": 8,
    }
    core_api.get(f"{CP}/requests/7/raw").respond(200, json=raw)

    data = (
        await agent.call_tool("get_request_raw", {"context_uuid": CTX, "request_id": 7})
    ).structured_content or {}

    assert data["headText"] == HEAD
    assert data["rawRequestText"] == HEAD + '{"u":"a"}'
    assert data["rawRequestBase64"] == b64(HEAD + '{"u":"a"}')
    assert data["responseId"] == 8
    assert "notes" not in data


async def test_get_request_raw_without_a_body_gives_the_head_as_the_whole_message(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    head = "GET /api/users/42 HTTP/1.1\r\nHost: api.example.com\r\n\r\n"
    core_api.get(f"{CP}/requests/7/raw").respond(
        200, json={"rawHeadBase64": b64(head), "rawBodyBase64": None, "bodyStoredAsFile": False}
    )

    data = (
        await agent.call_tool("get_request_raw", {"context_uuid": CTX, "request_id": 7})
    ).structured_content or {}

    assert data["rawRequestText"] == head
    assert data["rawRequestBase64"] == b64(head)
    assert "responseId" not in data


async def test_get_request_raw_reads_a_body_stored_as_a_file(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/requests/7/raw").respond(
        200, json={"rawHeadBase64": b64(HEAD), "rawBodyBase64": None, "bodyStoredAsFile": True}
    )
    body_route(core_api, b'{"large": true}')

    data = (
        await agent.call_tool("get_request_raw", {"context_uuid": CTX, "request_id": 7})
    ).structured_content or {}

    assert data["bodyStoredAsFile"] is True
    assert data["bodyText"] == '{"large": true}'
    assert data["rawRequestBase64"] == b64(HEAD + '{"large": true}')


# ---------------------------------------------------------------------------- get_request_body


async def test_get_request_body_reads_a_slice(agent: Agent, core_api: respx.MockRouter) -> None:
    body_route(core_api, b"0123456789", "text/plain")

    data = (
        await agent.call_tool(
            "get_request_body", {"context_uuid": CTX, "request_id": 7, "offset": 2, "max_bytes": 3}
        )
    ).structured_content or {}

    assert data["text"] == "234"
    assert (data["offset"], data["returnedBytes"], data["totalBytes"]) == (2, 3, 10)
    assert data["truncated"] is True
    assert data["nextOffset"] == 5


async def test_get_request_body_without_arguments_reads_from_the_start(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    body_route(core_api, b"hello", "text/plain; charset=utf-8")

    data = (
        await agent.call_tool("get_request_body", {"context_uuid": CTX, "request_id": 7})
    ).structured_content or {}

    assert data["text"] == "hello"
    assert data["truncated"] is False


async def test_get_request_body_describes_a_binary_body(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    body_route(core_api, b"\x89PNG\r\n", "image/png")

    data = (
        await agent.call_tool("get_request_body", {"context_uuid": CTX, "request_id": 7})
    ).structured_content

    assert data == {"binary": True, "contentType": "image/png", "sizeBytes": 6}


async def test_a_request_without_a_body_is_not_found_with_a_hint(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/requests/7/body/stream").respond(404, text="Request has no body")

    result = await agent.call_tool("get_request_body", {"context_uuid": CTX, "request_id": 7})

    assert result.is_error
    assert "Request has no body" in text_of(result)
    assert "may simply have no body" in text_of(result)


@pytest.mark.parametrize("arguments", [{"offset": -1}, {"max_bytes": 0}])
async def test_body_arguments_out_of_range_are_refused_before_any_call(
    agent: Agent, core_api: respx.MockRouter, arguments: dict[str, int]
) -> None:
    result = await agent.call_tool(
        "get_request_body", {"context_uuid": CTX, "request_id": 7, **arguments}
    )

    assert result.is_error
    assert not [c for c in core_api.calls if "/requests/" in c.request.url.path]


async def test_an_offset_beyond_what_the_server_reads_is_explained(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    body_route(core_api, b"x" * 10, "text/plain")

    result = await agent.call_tool(
        "get_request_body", {"context_uuid": CTX, "request_id": 7, "offset": 9_999_999}
    )

    assert result.is_error
    assert "beyond the 5242880 bytes" in text_of(result)


@pytest.mark.parametrize("tool", TOOLS)
async def test_a_bad_uuid_never_reaches_the_core(
    agent: Agent, core_api: respx.MockRouter, tool: str
) -> None:
    result = await agent.call_tool(tool, {"context_uuid": "nope", "request_id": 7})

    assert result.is_error
    assert "context_uuid must be a UUID" in text_of(result)
    assert not [c for c in core_api.calls if c.request.url.path.startswith("/api/agent-contexts")]
