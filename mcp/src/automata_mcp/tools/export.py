"""The tool that exports the field names seen on the context's host."""

from typing import Annotated, Any

from mcp.server.mcpserver import Context, MCPServer
from pydantic import Field

from automata_mcp.services import ingestion as ingestion_service
from automata_mcp.state import AppState, get_state
from automata_mcp.tools.common import READ_ONLY, ContextUuid, parse_context_uuid


def register(mcp: MCPServer[AppState]) -> None:
    @mcp.tool(annotations=READ_ONLY)
    async def export_body_property_names(
        context_uuid: ContextUuid,
        ctx: Context[AppState, Any],
        max_names: Annotated[
            int,
            Field(ge=1, description="Most names to return; `truncated` says if there are more."),
        ] = 2000,
    ) -> dict[str, Any]:
        """Returns all the JSON and form field names seen in the requests and responses of your
        context's host: `names`, `total` (how many there are) and `truncated` (true if `max_names`
        cut the list).

        Useful as a parameter wordlist: it tells you which property names the application really
        uses, for example to look for hidden or mass-assignable parameters. To fuzz with them,
        put them in a wordlist with create_wordlist. It covers the whole host, not only the
        requests of your context, and only the names, never their values.
        """
        state = get_state(ctx)
        return await ingestion_service.body_property_names(
            state.core, parse_context_uuid(context_uuid), max_names
        )
