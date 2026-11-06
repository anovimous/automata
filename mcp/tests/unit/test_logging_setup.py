import logging
import sys
from collections.abc import AsyncIterator, Iterator

import pytest
import respx
from mcp import Client
from mcp.server.mcpserver import MCPServer

from automata_mcp.config import Settings
from automata_mcp.logging_setup import (
    HANDLER_NAME,
    _context_uuid,
    _is_error,
    configure_logging,
    log_tool_calls,
)
from automata_mcp.server import create_server
from automata_mcp.state import AppState

BASE = "http://core.test:8080"
CTX = "6f1c0a52-0000-4000-8000-000000000001"
ROUTINES = {"content": [], "page": 0, "size": 1, "totalElements": 0, "totalPages": 0, "last": True}


@pytest.fixture
def restore_logging() -> Iterator[None]:
    root = logging.getLogger()
    package = logging.getLogger("automata_mcp")
    handlers, level, package_level = list(root.handlers), root.level, package.level
    yield
    root.handlers[:] = handlers
    root.setLevel(level)
    package.setLevel(package_level)


@pytest.fixture
async def server() -> AsyncIterator[MCPServer[AppState]]:
    with respx.mock(base_url=BASE, assert_all_called=False) as router:
        router.get("/api/routines").respond(200, json=ROUTINES)
        settings = Settings(_env_file=None, core_url=BASE, get_retries=0)  # type: ignore[call-arg]
        mcp = create_server(settings)

        @mcp.tool()
        async def echo(context_uuid: str, secret: str) -> str:
            return f"result for {secret}"

        @mcp.tool()
        async def fail(context_uuid: str, secret: str) -> str:
            raise RuntimeError("secret detail")

        yield mcp


# ---------------------------------------------------------------------------- configure_logging


@pytest.mark.usefixtures("restore_logging")
def test_configure_logging_writes_to_stderr_and_sets_the_levels() -> None:
    configure_logging("DEBUG")

    root = logging.getLogger()
    handler = next(h for h in root.handlers if h.get_name() == HANDLER_NAME)
    assert isinstance(handler, logging.StreamHandler)
    assert handler.stream is sys.stderr
    assert root.level == logging.WARNING
    assert logging.getLogger("automata_mcp").level == logging.DEBUG


@pytest.mark.usefixtures("restore_logging")
def test_configure_logging_can_be_called_twice() -> None:
    configure_logging("INFO")
    configure_logging("ERROR")

    named = [h for h in logging.getLogger().handlers if h.get_name() == HANDLER_NAME]
    assert len(named) == 1
    assert logging.getLogger("automata_mcp").level == logging.ERROR


# ---------------------------------------------------------------------------- helpers


@pytest.mark.parametrize(
    ("arguments", "expected"),
    [
        ({"context_uuid": CTX}, CTX),
        ({"context_uuid": 5}, None),
        ({}, None),
        (None, None),
        ("x", None),
    ],
)
def test_context_uuid_is_read_from_the_arguments(arguments: object, expected: str | None) -> None:
    assert _context_uuid(arguments) == expected


def test_is_error_reads_a_result_object_or_a_dict() -> None:
    from mcp.types import CallToolResult

    assert _is_error(CallToolResult(content=[], is_error=True)) is True
    assert _is_error(CallToolResult(content=[], is_error=False)) is False
    assert _is_error({"isError": True}) is True
    assert _is_error({"content": []}) is False
    assert _is_error(None) is False


# ---------------------------------------------------------------------------- log_tool_calls


async def test_a_successful_call_is_logged_with_tool_context_outcome_and_duration(
    server: MCPServer[AppState], caplog: pytest.LogCaptureFixture
) -> None:
    caplog.set_level(logging.INFO, logger="automata_mcp.tools")

    async with Client(server) as client:
        await client.call_tool("echo", {"context_uuid": CTX, "secret": "tenant-token"})

    line = next(r.getMessage() for r in caplog.records if r.name == "automata_mcp.tools")
    assert line.startswith(f"tool=echo context={CTX} outcome=ok duration_ms=")


async def test_a_failed_call_is_logged_as_an_error(
    server: MCPServer[AppState], caplog: pytest.LogCaptureFixture
) -> None:
    caplog.set_level(logging.INFO, logger="automata_mcp.tools")

    async with Client(server) as client:
        await client.call_tool("fail", {"context_uuid": CTX, "secret": "tenant-token"})

    line = next(r.getMessage() for r in caplog.records if r.name == "automata_mcp.tools")
    assert "tool=fail" in line
    assert "outcome=error" in line


async def test_arguments_and_results_are_never_logged(
    server: MCPServer[AppState], caplog: pytest.LogCaptureFixture
) -> None:
    caplog.set_level(logging.DEBUG)

    async with Client(server) as client:
        await client.call_tool("echo", {"context_uuid": CTX, "secret": "tenant-token"})

    tool_lines = [r.getMessage() for r in caplog.records if r.name == "automata_mcp.tools"]
    assert tool_lines
    assert not any("tenant-token" in line for line in tool_lines)


async def test_requests_that_are_not_tool_calls_are_not_logged(
    server: MCPServer[AppState], caplog: pytest.LogCaptureFixture
) -> None:
    caplog.set_level(logging.INFO, logger="automata_mcp.tools")

    async with Client(server) as client:
        await client.list_tools()

    assert not [r for r in caplog.records if r.name == "automata_mcp.tools"]


async def test_the_middleware_logs_an_exception_that_escapes(
    caplog: pytest.LogCaptureFixture,
) -> None:
    from mcp.server.context import ServerRequestContext

    caplog.set_level(logging.INFO, logger="automata_mcp.tools")

    async def failing(_: object) -> None:
        raise RuntimeError("boom")

    ctx = ServerRequestContext(
        session=None,  # type: ignore[arg-type]
        lifespan_context=None,
        protocol_version="x",
        method="tools/call",
        params={"name": "t", "arguments": {"context_uuid": CTX}},
    )
    with pytest.raises(RuntimeError):
        await log_tool_calls(ctx, failing)  # type: ignore[arg-type]

    assert f"tool=t context={CTX} outcome=error" in caplog.text
