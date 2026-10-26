"""The response tools, called through MCP against a fake core."""

import base64
from collections.abc import Callable
from typing import Any

import pytest
import respx
from mcp.types import CallToolResult

from helpers import Agent

CTX = "6f1c0a52-0000-4000-8000-000000000001"
CP = f"/api/agent-contexts/uuid/{CTX}"
TOOLS = ["get_response", "get_response_raw", "get_response_body"]
HEAD = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n"


def text_of(result: CallToolResult) -> str:
    return result.content[0].text  # type: ignore[union-attr]


def b64(text: str) -> str:
    return base64.b64encode(text.encode()).decode()


def body_route(
    core: respx.MockRouter, content: bytes, content_type: str = "application/json"
) -> respx.Route:
    return core.get(f"{CP}/requests/7/response/body/stream").respond(
        200, content=content, headers={"Content-Type": content_type}
    )


async def test_the_three_response_tools_are_read_only_and_take_a_context(agent: Agent) -> None:
    tools = {t.name: t for t in (await agent.list_tools()).tools if t.name in TOOLS}

    assert set(tools) == set(TOOLS)
    for name, tool in tools.items():
        assert tool.annotations is not None
        assert tool.annotations.read_only_hint is True
        assert (
            tool.input_schema["required"] == ["context_uuid", "request_id"]
            or name == "get_response"
        )
        assert "ctx" not in tool.input_schema["properties"], name
        assert len(tool.description or "") > 150, name


# ---------------------------------------------------------------------------- get_response


async def test_get_response_returns_status_headers_and_the_text_of_the_body(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/requests/7/response/metadata").respond(
        200, json=load_fixture("response_metadata")
    )
    body_route(core_api, b'{"id": 42, "email": "b@example.com"}')

    data = (
        await agent.call_tool("get_response", {"context_uuid": CTX, "request_id": 7})
    ).structured_content or {}

    assert data["statusCode"] == 200
    assert data["headers"] == [{"name": "Content-Type", "value": "application/json"}]
    assert data["body"]["storage"] == "FILE"
    assert data["body"]["text"] == '{"id": 42, "email": "b@example.com"}'
    assert data["responseId"] == 8


async def test_get_response_without_body_text_when_not_asked(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/requests/7/response/metadata").respond(
        200, json=load_fixture("response_metadata")
    )
    route = body_route(core_api, b"not read")

    data = (
        await agent.call_tool(
            "get_response", {"context_uuid": CTX, "request_id": 7, "include_body": False}
        )
    ).structured_content or {}

    assert "text" not in data["body"]
    assert route.call_count == 0


async def test_get_response_cuts_at_body_max_bytes(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/requests/7/response/metadata").respond(
        200, json=load_fixture("response_metadata")
    )
    body_route(core_api, b"x" * 100, "text/plain")

    body = (
        await agent.call_tool(
            "get_response", {"context_uuid": CTX, "request_id": 7, "body_max_bytes": 10}
        )
    ).structured_content["body"]  # type: ignore[index]

    assert body["returnedBytes"] == 10
    assert body["nextOffset"] == 10


async def test_a_request_without_a_response_says_so(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/requests/7/response/metadata").respond(404, text="Response not found")

    result = await agent.call_tool("get_response", {"context_uuid": CTX, "request_id": 7})

    assert result.is_error
    assert "Response not found" in text_of(result)
    assert "has no stored response" in text_of(result)


# ---------------------------------------------------------------------------- get_response_raw


async def test_get_response_raw_gives_head_and_text_body_but_no_request_forms(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/requests/7/response/raw").respond(
        200,
        json={
            "rawHeadBase64": b64(HEAD),
            "rawBodyBase64": b64('{"ok":true}'),
            "bodyStoredAsFile": False,
        },
    )

    data = (
        await agent.call_tool("get_response_raw", {"context_uuid": CTX, "request_id": 7})
    ).structured_content

    assert data == {"headText": HEAD, "bodyStoredAsFile": False, "bodyText": '{"ok":true}'}


async def test_get_response_raw_reads_a_body_stored_as_a_file(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/requests/7/response/raw").respond(200, json=load_fixture("response_raw"))
    body_route(core_api, b'{"file": true}')

    data = (
        await agent.call_tool("get_response_raw", {"context_uuid": CTX, "request_id": 7})
    ).structured_content or {}

    assert data["bodyStoredAsFile"] is True
    assert data["bodyText"] == '{"file": true}'


async def test_get_response_raw_of_a_request_without_a_response_says_so(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/requests/7/response/raw").respond(404, text="Response not found")

    result = await agent.call_tool("get_response_raw", {"context_uuid": CTX, "request_id": 7})

    assert result.is_error
    assert "has no stored response" in text_of(result)


# ---------------------------------------------------------------------------- get_response_body


async def test_get_response_body_reads_a_script_in_parts(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    script = b"function a(){return 1}\nfunction b(){return 2}\n"
    body_route(core_api, script, "application/javascript")

    first = (
        await agent.call_tool(
            "get_response_body", {"context_uuid": CTX, "request_id": 7, "max_bytes": 23}
        )
    ).structured_content or {}
    second = (
        await agent.call_tool(
            "get_response_body",
            {"context_uuid": CTX, "request_id": 7, "offset": first["nextOffset"], "max_bytes": 100},
        )
    ).structured_content or {}

    assert first["truncated"] is True
    assert first["text"] + second["text"] == script.decode()
    assert second["truncated"] is False
    assert second["totalBytes"] == len(script)


async def test_get_response_body_describes_a_binary_body(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    body_route(core_api, b"%PDF-1.7", "application/pdf")

    data = (
        await agent.call_tool("get_response_body", {"context_uuid": CTX, "request_id": 7})
    ).structured_content

    assert data == {"binary": True, "contentType": "application/pdf", "sizeBytes": 8}


async def test_a_missing_response_body_is_not_found_with_a_hint(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/requests/7/response/body/stream").respond(404, text="Response not found")

    result = await agent.call_tool("get_response_body", {"context_uuid": CTX, "request_id": 7})

    assert result.is_error
    assert "no stored response, or the response may have no body" in text_of(result)


@pytest.mark.parametrize("tool", TOOLS)
async def test_a_bad_uuid_never_reaches_the_core(
    agent: Agent, core_api: respx.MockRouter, tool: str
) -> None:
    result = await agent.call_tool(tool, {"context_uuid": "nope", "request_id": 7})

    assert result.is_error
    assert "context_uuid must be a UUID" in text_of(result)
    assert not [c for c in core_api.calls if c.request.url.path.startswith("/api/agent-contexts")]
