"""The tool that records a request the agent observed."""

from typing import Annotated, Any

from mcp.server.mcpserver import Context, MCPServer
from pydantic import Field

from automata_mcp.core.errors import CoreNotFound
from automata_mcp.services import ingestion as ingestion_service
from automata_mcp.state import AppState, get_state
from automata_mcp.tools.common import CREATES, hints, parse_context_uuid

ADD_404 = (
    "Here a 404 means no host has that name (hosts are managed by humans; you cannot create one) "
    "or, if you gave context_uuid, that context does not exist."
)


def register(mcp: MCPServer[AppState]) -> None:
    @mcp.tool(annotations=CREATES)
    async def add_request(
        host: Annotated[
            str, Field(description="Name of the host exactly as stored, e.g. api.example.com.")
        ],
        ctx: Context[AppState, Any],
        raw_request: Annotated[
            str | None,
            Field(description="The request as text: start line, headers, blank line, body."),
        ] = None,
        raw_request_base64: Annotated[
            str | None, Field(description="The request as base64 of its exact bytes.")
        ] = None,
        raw_response: Annotated[
            str | None, Field(description="The response as text, if you observed one.")
        ] = None,
        raw_response_base64: Annotated[
            str | None, Field(description="The response as base64 of its exact bytes.")
        ] = None,
        tenant_id: Annotated[
            int | None,
            Field(description="Tenant the request belongs to; needs context_uuid."),
        ] = None,
        context_uuid: Annotated[
            str | None,
            Field(description="Context whose tenant list `tenant_id` is checked against."),
        ] = None,
    ) -> dict[str, Any]:
        """Records a request (and, if you saw it, its response) that you observed or built, for
        a host the core knows. Use it for traffic you discovered while testing, such as a request
        you sent yourself or one found in a script.

        Give the request as `raw_request` (text: LF line endings are fine, the head gets CRLF and
        a closing blank line, the body is kept as written, Content-Length is not recomputed) or as
        `raw_request_base64`, exactly one of the two; the response likewise, at most one of
        `raw_response` / `raw_response_base64`. `host` is the host name as stored; it may be any
        host. `tenant_id` needs `context_uuid`, and the tenant must be in that context and belong
        to the request's host. The core parses the messages and checks every rule; its message is
        shown if one is broken.

        The request is stored with source AI and is NOT part of any context: you cannot read it
        back or create jobs on it until a human adds it to a context. Returns the stored
        request's summary (`requestId`, `responseId`, `hostId`, `method`, `computatedPath`,
        `source`, ...) and a note saying so.
        """
        state = get_state(ctx)
        with hints({CoreNotFound: ADD_404}):
            return await ingestion_service.add_request(
                state.core,
                host=host,
                raw_request=raw_request,
                raw_request_base64=raw_request_base64,
                raw_response=raw_response,
                raw_response_base64=raw_response_base64,
                tenant_id=tenant_id,
                context_uuid=parse_context_uuid(context_uuid) if context_uuid else None,
            )
