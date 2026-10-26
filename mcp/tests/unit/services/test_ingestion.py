import base64
import json
from collections.abc import AsyncIterator, Callable
from typing import Any
from uuid import UUID

import pytest
import respx
from mcp.server.mcpserver.exceptions import ToolError

from automata_mcp.core.client import CoreClient
from automata_mcp.core.errors import CoreNotFound
from automata_mcp.services import ingestion as ingestion_service
from automata_mcp.services.ingestion import encode_message

BASE = "http://core.test:8080"
CTX = UUID("6f1c0a52-0000-4000-8000-000000000001")
CP = f"/api/agent-contexts/uuid/{CTX}"
REQUEST_TEXT = (
    'POST /api/login HTTP/1.1\nHost: api.example.com\nContent-Type: application/json\n\n{"u":"a"}'
)
REQUEST_BYTES = (
    b"POST /api/login HTTP/1.1\r\nHost: api.example.com\r\nContent-Type: application/json\r\n\r\n"
    b'{"u":"a"}'
)


@pytest.fixture
async def core() -> AsyncIterator[respx.MockRouter]:
    with respx.mock(base_url=BASE, assert_all_called=False) as router:
        yield router


@pytest.fixture
async def client() -> AsyncIterator[CoreClient]:
    async with CoreClient(BASE, get_retries=0) as instance:
        yield instance


async def add(client: CoreClient, **overrides: Any) -> dict[str, Any]:
    arguments: dict[str, Any] = {
        "host": "api.example.com",
        "raw_request": REQUEST_TEXT,
        "raw_request_base64": None,
        "raw_response": None,
        "raw_response_base64": None,
        "tenant_id": None,
        "context_uuid": None,
    }
    return await ingestion_service.add_request(client, **(arguments | overrides))


def sent(route: respx.Route) -> dict[str, Any]:
    return json.loads(route.calls.last.request.content)


# ---------------------------------------------------------------------------- encode_message


def kinds(text: str | None, base64_value: str | None, *, required: bool) -> str | None:
    return encode_message(
        text,
        base64_value,
        text_name="raw_request",
        base64_name="raw_request_base64",
        required=required,
    )


def test_text_is_normalized_and_encoded() -> None:
    assert (
        kinds("GET / HTTP/1.1\nHost: x\n\n", None, required=True)
        == base64.b64encode(b"GET / HTTP/1.1\r\nHost: x\r\n\r\n").decode()
    )


def test_base64_is_passed_on_untouched() -> None:
    assert kinds(None, "not even checked==", required=True) == "not even checked=="


def test_both_forms_are_an_error() -> None:
    with pytest.raises(ToolError, match="raw_request or raw_request_base64, not both"):
        kinds("GET / HTTP/1.1", "QQ==", required=False)


def test_a_missing_required_message_is_an_error() -> None:
    with pytest.raises(ToolError, match=r"raw_request \(text\) or raw_request_base64"):
        kinds(None, None, required=True)


def test_a_missing_optional_message_is_none() -> None:
    assert kinds(None, None, required=False) is None


def test_an_empty_text_counts_as_given() -> None:
    assert kinds("", None, required=True) == base64.b64encode(b"\r\n\r\n").decode()


# ---------------------------------------------------------------------------- add_request


async def test_a_request_given_as_text_is_stored_with_its_normalized_bytes(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core.post("/api/external/ai/requests").respond(
        201, json=load_fixture("request_summary")
    )

    result = await add(client)

    body = sent(route)
    assert body == {
        "requestBase64": base64.b64encode(REQUEST_BYTES).decode(),
        "host": "api.example.com",
    }
    assert result["requestId"] == 31
    assert result["source"] == "AI"
    assert result["responseId"] == 32
    assert result["note"] == ingestion_service.NOT_IN_A_CONTEXT


async def test_the_note_says_the_request_cannot_be_read_back() -> None:
    note = ingestion_service.NOT_IN_A_CONTEXT

    assert "not part of any context" in note
    assert "until a human adds it to a context" in note


async def test_base64_forms_are_sent_as_they_are(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core.post("/api/external/ai/requests").respond(
        201, json=load_fixture("request_summary")
    )

    await add(client, raw_request=None, raw_request_base64="UkVR", raw_response_base64="UkVT")

    assert sent(route) == {
        "requestBase64": "UkVR",
        "host": "api.example.com",
        "base64Response": "UkVT",
    }


async def test_a_response_given_as_text_is_normalized_too(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core.post("/api/external/ai/requests").respond(
        201, json=load_fixture("request_summary")
    )

    await add(client, raw_response='HTTP/1.1 200 OK\nContent-Type: application/json\n\n{"ok":true}')

    assert base64.b64decode(sent(route)["base64Response"]) == (
        b'HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{"ok":true}'
    )


async def test_tenant_and_context_are_sent_for_the_core_to_check(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core.post("/api/external/ai/requests").respond(
        201, json=load_fixture("request_summary")
    )

    await add(client, tenant_id=4, context_uuid=CTX)

    assert sent(route)["tenantId"] == 4
    assert sent(route)["agentContextUuid"] == "6f1c0a52-0000-4000-8000-000000000001"


async def test_a_tenant_without_a_context_is_left_for_the_core_to_refuse(
    client: CoreClient, core: respx.MockRouter
) -> None:
    from automata_mcp.core.errors import CoreBadRequest

    route = core.post("/api/external/ai/requests").respond(
        400, text="tenantId needs agentContextUuid"
    )

    with pytest.raises(CoreBadRequest, match="tenantId needs agentContextUuid"):
        await add(client, tenant_id=4)

    assert "agentContextUuid" not in sent(route)


async def test_the_forms_are_checked_before_anything_is_sent(
    client: CoreClient, core: respx.MockRouter
) -> None:
    with pytest.raises(ToolError):
        await add(client, raw_request=None)
    with pytest.raises(ToolError):
        await add(client, raw_request_base64="QQ==")
    with pytest.raises(ToolError):
        await add(client, raw_response="HTTP/1.1 200 OK", raw_response_base64="QQ==")

    assert core.calls.call_count == 0


async def test_an_unknown_host_is_the_core_error(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post("/api/external/ai/requests").respond(404, text="Host not found")

    with pytest.raises(CoreNotFound, match="Host not found"):
        await add(client, host="nope.example.com")


# ---------------------------------------------------------------------------- body_property_names


async def test_the_names_of_the_hosts_of_the_context_are_exported_with_the_host_id_only(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core.get(f"{CP}/host").respond(200, json=load_fixture("host_detailed"))
    export = core.get("/api/export/body-properties").respond(200, text="id\nname\nemail\n")

    result = await ingestion_service.body_property_names(client, CTX, 2000)

    assert result == {"names": ["id", "name", "email"], "truncated": False, "total": 3}
    assert dict(export.calls.last.request.url.params) == {"hostId": "2"}


async def test_the_list_is_cut_at_max_names_and_says_so(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core.get(f"{CP}/host").respond(200, json=load_fixture("host_detailed"))
    core.get("/api/export/body-properties").respond(200, text="a\nb\nc\nd\n")

    result = await ingestion_service.body_property_names(client, CTX, 2)

    assert result == {"names": ["a", "b"], "truncated": True, "total": 4}


async def test_exactly_max_names_is_not_truncated(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core.get(f"{CP}/host").respond(200, json=load_fixture("host_detailed"))
    core.get("/api/export/body-properties").respond(200, text="a\nb\n")

    assert (await ingestion_service.body_property_names(client, CTX, 2))["truncated"] is False


async def test_a_host_without_names_gives_an_empty_list(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core.get(f"{CP}/host").respond(200, json=load_fixture("host_detailed"))
    core.get("/api/export/body-properties").respond(200, text="")

    assert await ingestion_service.body_property_names(client, CTX, 10) == {
        "names": [],
        "truncated": False,
        "total": 0,
    }


async def test_an_unknown_context_stops_before_the_export(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.get(f"{CP}/host").respond(404, text="Agent context not found")
    export = core.get("/api/export/body-properties").respond(200, text="a\n")

    with pytest.raises(CoreNotFound):
        await ingestion_service.body_property_names(client, CTX, 10)

    assert export.call_count == 0
