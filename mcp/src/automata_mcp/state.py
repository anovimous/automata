from dataclasses import dataclass
from typing import Any

from mcp.server.mcpserver import Context

from automata_mcp.config import Settings
from automata_mcp.core.client import CoreClient


@dataclass(frozen=True)
class AppState:
    """What every tool needs, created once by the lifespan of the server."""

    core: CoreClient
    settings: Settings


def get_state(ctx: Context[AppState, Any]) -> AppState:
    """The ``AppState`` of the running server, from the ``Context`` a tool receives."""
    return ctx.request_context.lifespan_context
