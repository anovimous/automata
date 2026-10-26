import logging
from collections.abc import AsyncIterator
from typing import Any

import httpx
import pytest
import respx
from mcp import Client
from mcp.server.mcpserver import Context, MCPServer

from automata_mcp.config import Settings
from automata_mcp.core.client import CoreClient
from automata_mcp.core.errors import error_from_response
from automata_mcp.server import (
    INSTRUCTIONS,
    SERVER_NAME,
    check_core,
    create_server,
    http_run_options,
    run_server,
)
from automata_mcp.state import AppState, get_state

BASE = "http://core.test:8080"
ROUTINES = {"content": [], "page": 0, "size": 1, "totalElements": 0, "totalPages": 0, "last": True}


@pytest.fixture
def settings() -> Settings:
    return Settings(_env_file=None, core_url=BASE, get_retries=0)  # type: ignore[call-arg]


@pytest.fixture
async def core() -> AsyncIterator[respx.MockRouter]:
    with respx.mock(base_url=BASE, assert_all_called=False) as router:
        router.get("/api/routines").respond(200, json=ROUTINES)
        yield router


# ---------------------------------------------------------------------------- check_core


async def test_check_core_reports_a_reachable_core(
    core: respx.MockRouter, caplog: pytest.LogCaptureFixture
) -> None:
    caplog.set_level(logging.INFO, logger="automata_mcp")

    async with CoreClient(BASE, get_retries=0) as client:
        assert await check_core(client) is True

    assert f"The core at {BASE} is reachable." in caplog.text
    assert core.calls.last.request.url.params["size"] == "1"


async def test_check_core_warns_when_the_core_is_unreachable(
    core: respx.MockRouter, caplog: pytest.LogCaptureFixture
) -> None:
    core.get("/api/routines").mock(side_effect=httpx.ConnectError("refused"))

    async with CoreClient(BASE, get_retries=0) as client:
        assert await check_core(client) is False

    warning = next(record for record in caplog.records if record.levelno == logging.WARNING)
    assert BASE in warning.getMessage()
    assert "starts anyway" in warning.getMessage()


async def test_check_core_warns_when_the_core_answers_with_an_error(
    core: respx.MockRouter, caplog: pytest.LogCaptureFixture
) -> None:
    core.get("/api/routines").respond(500, text="database down")

    async with CoreClient(BASE, get_retries=0) as client:
        assert await check_core(client) is False

    assert "answered with an error" in caplog.text
    assert "database down" in caplog.text


# ---------------------------------------------------------------------------- create_server


def test_the_server_has_a_name_and_instructions(settings: Settings) -> None:
    server = create_server(settings)

    assert server.name == SERVER_NAME
    assert server.instructions == INSTRUCTIONS
    assert "context_uuid" in INSTRUCTIONS


async def test_the_lifespan_gives_tools_the_state_and_closes_the_core(
    core: respx.MockRouter, settings: Settings
) -> None:
    server = create_server(settings)
    seen: list[AppState] = []

    @server.tool()
    async def probe(ctx: Context[AppState, Any]) -> str:
        seen.append(get_state(ctx))
        return get_state(ctx).core.base_url

    async with Client(server) as client:
        result = await client.call_tool("probe", {})

        assert result.content[0].text == BASE  # type: ignore[union-attr]
        assert not seen[0].core._http.is_closed

    assert seen[0].settings is settings
    assert seen[0].core._http.is_closed


async def test_the_lifespan_checks_the_core_when_it_starts(
    core: respx.MockRouter, settings: Settings
) -> None:
    async with Client(create_server(settings)):
        assert core.calls.call_count == 1


async def test_the_server_serves_even_when_the_core_is_down(
    core: respx.MockRouter, settings: Settings
) -> None:
    core.get("/api/routines").mock(side_effect=httpx.ConnectError("refused"))
    server = create_server(settings)

    @server.tool()
    async def ping() -> str:
        return "pong"

    async with Client(server) as client:
        result = await client.call_tool("ping", {})

    assert result.content[0].text == "pong"  # type: ignore[union-attr]


async def test_a_core_error_reaches_the_agent_with_its_guidance(
    core: respx.MockRouter, settings: Settings
) -> None:
    server = create_server(settings)

    @server.tool()
    async def missing() -> str:
        raise error_from_response(404, "Request not found in the agent context")

    async with Client(server) as client:
        result = await client.call_tool("missing", {})

    assert result.is_error
    text = result.content[0].text  # type: ignore[union-attr]
    assert "Not found, or not part of this context" in text
    assert "Request not found in the agent context" in text


async def test_an_unexpected_exception_is_hidden_from_the_agent(
    core: respx.MockRouter, settings: Settings
) -> None:
    server = create_server(settings)

    @server.tool()
    async def crash() -> str:
        raise RuntimeError("secret detail")

    async with Client(server) as client:
        result = await client.call_tool("crash", {})

    assert result.is_error
    assert "secret detail" not in result.content[0].text  # type: ignore[union-attr]


# ---------------------------------------------------------------------------- transports


def test_http_run_options_come_from_the_settings() -> None:
    settings = Settings(  # type: ignore[call-arg]
        _env_file=None, http_host="10.0.0.5", http_port=9001, http_max_request_bytes=1234
    )

    assert http_run_options(settings) == {
        "host": "10.0.0.5",
        "port": 9001,
        "max_request_body_size": 1234,
    }


def run_calls(
    monkeypatch: pytest.MonkeyPatch, server: MCPServer[AppState], settings: Settings
) -> list[tuple[tuple[Any, ...], dict[str, Any]]]:
    calls: list[tuple[tuple[Any, ...], dict[str, Any]]] = []
    monkeypatch.setattr(server, "run", lambda *args, **kwargs: calls.append((args, kwargs)))
    run_server(server, settings)
    return calls


def test_run_server_uses_stdio_by_default(
    monkeypatch: pytest.MonkeyPatch, settings: Settings
) -> None:
    assert run_calls(monkeypatch, create_server(settings), settings) == [(("stdio",), {})]


def test_run_server_uses_streamable_http_with_the_configured_address(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    http_settings = Settings(  # type: ignore[call-arg]
        _env_file=None, transport="streamable-http", http_host="0.0.0.0", http_port=8123
    )

    calls = run_calls(monkeypatch, create_server(http_settings), http_settings)

    assert calls == [
        (
            ("streamable-http",),
            {"host": "0.0.0.0", "port": 8123, "max_request_body_size": 4_194_304},
        )
    ]
