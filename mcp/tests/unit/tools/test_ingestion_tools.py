"""add_request and export_body_property_names, called through MCP against a fake core."""

import base64
import json
import logging
from collections.abc import Callable
from typing import Any

import pytest
import respx
from mcp.types import CallToolResult

from helpers import Agent

CTX = "6f1c0a52-0000-4000-8000-000000000001"
CP = f"/api/agent-contexts/uuid/{CTX}"
REQUEST = (
    'POST /api/login HTTP/1.1\nHost: api.example.com\nContent-Type: application/json\n\n{"u":"a"}'
)


def text_of(result: CallToolResult) -> str:
    return result.content[0].text  # type: ignore[union-attr]


async def add(agent: Agent, **extra: Any) -> CallToolResult:
    return await agent.call_tool(
        "add_request", {"host": "api.example.com", "raw_request": REQUEST} | extra
    )


# ---------------------------------------------------------------------------- the tool list


async def test_add_request_is_a_non_idempotent_write_that_needs_no_context(agent: Agent) -> None:
    tool = next(t for t in (await agent.list_tools()).tools if t.name == "add_request")

    assert tool.input_schema["required"] == ["host"]
    assert "ctx" not in tool.input_schema["properties"]
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
    assert "NOT part of any context" in (tool.description or "")


async def test_export_is_read_only_and_needs_a_context(agent: Agent) -> None:
    tool = next(
        t for t in (await agent.list_tools()).tools if t.name == "export_body_property_names"
    )

    assert tool.input_schema["required"] == ["context_uuid"]
    assert tool.input_schema["properties"]["max_names"]["default"] == 2000
    assert tool.input_schema["properties"]["max_names"]["minimum"] == 1
    assert tool.annotations is not None
    assert tool.annotations.read_only_hint is True


# ---------------------------------------------------------------------------- add_request


async def test_add_request_stores_the_request_and_says_it_is_in_no_context(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    post = core_api.post("/api/external/ai/requests").respond(
        201, json=load_fixture("request_summary")
    )

    result = await add(agent, raw_response="HTTP/1.1 200 OK\n\nok", tenant_id=4, context_uuid=CTX)

    data = result.structured_content or {}
    assert data["requestId"] == 31
    assert data["method"] == "POST"
    assert data["source"] == "AI"
    assert "until a human adds it to a context" in data["note"]
    body = json.loads(post.calls.last.request.content)
    assert body["host"] == "api.example.com"
    assert body["tenantId"] == 4
    assert body["agentContextUuid"] == CTX
    assert base64.b64decode(body["requestBase64"]).startswith(b"POST /api/login HTTP/1.1\r\nHost:")
    assert base64.b64decode(body["base64Response"]) == b"HTTP/1.1 200 OK\r\n\r\nok"


async def test_add_request_accepts_base64(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    post = core_api.post("/api/external/ai/requests").respond(
        201, json=load_fixture("request_summary")
    )

    result = await agent.call_tool(
        "add_request", {"host": "api.example.com", "raw_request_base64": "UkVR"}
    )

    assert not result.is_error
    assert json.loads(post.calls.last.request.content)["requestBase64"] == "UkVR"


async def test_an_unknown_host_gets_one_hint_for_host_and_context(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.post("/api/external/ai/requests").respond(404, text="Host not found")

    result = await add(agent, host="nope.example.com")

    assert result.is_error
    assert "Host not found" in text_of(result)
    assert "hosts are managed by humans" in text_of(result)
    assert "that context does not exist" in text_of(result)


async def test_the_parser_message_of_the_core_is_shown(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.post("/api/external/ai/requests").respond(400, text="Cannot parse the request line")

    result = await add(agent)

    assert result.is_error
    assert "Cannot parse the request line" in text_of(result)


async def test_a_tenant_without_a_context_is_for_the_core_to_refuse(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    post = core_api.post("/api/external/ai/requests").respond(
        400, text="tenantId needs agentContextUuid"
    )

    result = await add(agent, tenant_id=4)

    assert result.is_error
    assert "tenantId needs agentContextUuid" in text_of(result)
    assert "agentContextUuid" not in json.loads(post.calls.last.request.content)


@pytest.mark.parametrize(
    ("arguments", "message"),
    [
        ({"raw_request": None}, "Give the message"),
        ({"raw_request_base64": "QQ=="}, "not both"),
        ({"raw_response": "HTTP/1.1 200 OK", "raw_response_base64": "QQ=="}, "not both"),
        ({"context_uuid": "nope"}, "context_uuid must be a UUID"),
    ],
)
async def test_a_call_that_is_malformed_never_reaches_the_core(
    agent: Agent, core_api: respx.MockRouter, arguments: dict[str, Any], message: str
) -> None:
    result = await add(agent, **arguments)

    assert result.is_error
    assert message in text_of(result)
    assert not [c for c in core_api.calls if c.request.method == "POST"]


async def test_the_request_text_is_never_logged(
    agent: Agent,
    core_api: respx.MockRouter,
    load_fixture: Callable[[str], Any],
    caplog: pytest.LogCaptureFixture,
) -> None:
    core_api.post("/api/external/ai/requests").respond(201, json=load_fixture("request_summary"))
    caplog.set_level(logging.DEBUG)

    await add(agent, raw_request="POST /secret HTTP/1.1\nAuthorization: Bearer top-secret\n\n")

    assert "top-secret" not in caplog.text
    assert "tool=add_request" in caplog.text


# ---------------------------------------------------------------------------- export


async def test_export_returns_the_names_of_the_host_of_the_context(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/host").respond(200, json=load_fixture("host_detailed"))
    export = core_api.get("/api/export/body-properties").respond(200, text="id\nemail\nrole\n")

    result = await agent.call_tool("export_body_property_names", {"context_uuid": CTX})

    assert result.structured_content == {
        "names": ["id", "email", "role"],
        "truncated": False,
        "total": 3,
    }
    assert dict(export.calls.last.request.url.params) == {"hostId": "2"}


async def test_export_never_sends_a_program_id(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/host").respond(200, json=load_fixture("host_detailed") | {"programId": 1})
    core_api.get("/api/export/body-properties").respond(200, text="id\n")

    await agent.call_tool("export_body_property_names", {"context_uuid": CTX})

    exports = [c for c in core_api.calls if c.request.url.path == "/api/export/body-properties"]
    assert exports
    assert all("programId" not in c.request.url.params for c in exports)


async def test_export_cuts_the_list_at_max_names(
    agent: Agent, core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core_api.get(f"{CP}/host").respond(200, json=load_fixture("host_detailed"))
    core_api.get("/api/export/body-properties").respond(200, text="a\nb\nc\n")

    result = await agent.call_tool(
        "export_body_property_names", {"context_uuid": CTX, "max_names": 2}
    )

    assert result.structured_content == {"names": ["a", "b"], "truncated": True, "total": 3}


async def test_export_refuses_a_max_names_below_one(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    result = await agent.call_tool(
        "export_body_property_names", {"context_uuid": CTX, "max_names": 0}
    )

    assert result.is_error
    assert not [c for c in core_api.calls if c.request.url.path.startswith("/api/agent-contexts")]


async def test_export_of_an_unknown_context_is_not_found(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    core_api.get(f"{CP}/host").respond(404, text="Agent context not found")

    result = await agent.call_tool("export_body_property_names", {"context_uuid": CTX})

    assert result.is_error
    assert "Not found, or not part of this context" in text_of(result)


async def test_export_with_a_bad_uuid_never_reaches_the_core(
    agent: Agent, core_api: respx.MockRouter
) -> None:
    result = await agent.call_tool("export_body_property_names", {"context_uuid": "nope"})

    assert result.is_error
    assert "context_uuid must be a UUID" in text_of(result)
    assert not [
        c
        for c in core_api.calls
        if c.request.url.path.startswith("/api") and c.request.url.path != "/api/routines"
    ]
