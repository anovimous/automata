"""Read-only resources for clients that browse them (plan section 10.2).

The tools cover everything; these only give the same two documents a stable address:
the briefing of a context and the detail of a routine, both as JSON.
"""

import json
from collections.abc import Iterator
from contextlib import contextmanager
from typing import Any

from mcp.server.mcpserver import Context, MCPServer
from mcp.server.mcpserver.exceptions import ResourceError, ResourceNotFoundError, ToolError

from automata_mcp.core.errors import CoreNotFound
from automata_mcp.services.briefing import build_briefing
from automata_mcp.state import AppState, get_state
from automata_mcp.tools.common import parse_context_uuid


@contextmanager
def resource_errors() -> Iterator[None]:
    """Show a tool-style error to the client: only a ``ResourceError`` carries its message."""
    try:
        yield
    except CoreNotFound as error:
        raise ResourceNotFoundError(str(error)) from error
    except ToolError as error:
        raise ResourceError(str(error)) from error


def as_json(data: dict[str, Any]) -> str:
    return json.dumps(data, indent=2, ensure_ascii=False)


def register(mcp: MCPServer[AppState]) -> None:
    @mcp.resource(
        "automata://context/{context_uuid}/briefing",
        name="context-briefing",
        title="Briefing of a context",
        description=(
            "The same document as the get_context_briefing tool: the context, host, targets, "
            "tenants, allowed routines, recent jobs and findings, with notes. Tenant "
            "authentication data in it is sensitive."
        ),
        mime_type="application/json",
    )
    async def context_briefing(context_uuid: str, ctx: Context[AppState, Any]) -> str:
        state = get_state(ctx)
        with resource_errors():
            briefing = await build_briefing(
                state.core, state.settings, parse_context_uuid(context_uuid)
            )
        return as_json(briefing)

    @mcp.resource(
        "automata://routine/{routine_id}",
        name="routine",
        title="A routine with its config schema",
        description=(
            "The same document as the get_routine tool: the routine's description, configSchema, "
            "result type, what it can target and whether it needs a wordlist."
        ),
        mime_type="application/json",
    )
    async def routine(routine_id: str, ctx: Context[AppState, Any]) -> str:
        state = get_state(ctx)
        if not routine_id.isdigit():
            raise ResourceError(
                f"routine_id must be a whole number such as 1, got {routine_id[:40]!r}."
            )
        with resource_errors():
            detail = await state.core.get_routine(int(routine_id))
        return as_json(detail.to_output())
