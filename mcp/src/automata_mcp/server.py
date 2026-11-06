"""Builds the MCP server and runs it on the configured transport."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

from mcp.server.mcpserver import MCPServer

from automata_mcp import prompts, resources
from automata_mcp.config import Settings
from automata_mcp.core.client import CoreClient
from automata_mcp.core.errors import CoreError, CoreUnreachable
from automata_mcp.logging_setup import log_tool_calls
from automata_mcp.state import AppState
from automata_mcp.tools import register_all

logger = logging.getLogger(__name__)

SERVER_NAME = "automata"
INSTRUCTIONS = (
    "Tools to test the hosts of one Automata agent context, identified by the context_uuid the "
    "user gives you. The Automata core decides what you may see and do; when it refuses, its "
    "message says why."
)


async def check_core(core: CoreClient) -> bool:
    """Log whether the core answers. Never raises: tools report the problem when they are used."""
    try:
        await core.list_routines(size=1)
    except CoreUnreachable as error:
        logger.warning("%s The server starts anyway; tools fail until the core is up.", error)
        return False
    except CoreError as error:
        logger.warning("The core at %s answered with an error: %s", core.base_url, error)
        return False
    logger.info("The core at %s is reachable.", core.base_url)
    return True


def create_server(settings: Settings) -> MCPServer[AppState]:
    """The server: tools, the core client (created and closed by its lifespan) and the logging."""

    @asynccontextmanager
    async def lifespan(_: MCPServer[AppState]) -> AsyncIterator[AppState]:
        async with CoreClient.from_settings(settings) as core:
            await check_core(core)
            yield AppState(core=core, settings=settings)

    mcp = MCPServer[AppState](
        SERVER_NAME,
        instructions=INSTRUCTIONS,
        lifespan=lifespan,
        middleware=[log_tool_calls],
        log_level=settings.log_level,
    )
    register_all(mcp)
    prompts.register(mcp)
    resources.register(mcp)
    return mcp


def http_run_options(settings: Settings) -> dict[str, Any]:
    """The arguments of the streamable HTTP transport (the SDK's own defaults are not ours)."""
    return {
        "host": settings.http_host,
        "port": settings.http_port,
        "max_request_body_size": settings.http_max_request_bytes,
    }


def run_server(mcp: MCPServer[AppState], settings: Settings) -> None:
    """Run on stdio or streamable HTTP, as configured. Blocks until the server stops."""
    if settings.transport == "streamable-http":
        logger.info(
            "Serving MCP over streamable HTTP on %s:%s", settings.http_host, settings.http_port
        )
        mcp.run("streamable-http", **http_run_options(settings))
    else:
        logger.info("Serving MCP over stdio")
        mcp.run("stdio")
