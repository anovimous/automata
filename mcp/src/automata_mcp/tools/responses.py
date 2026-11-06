"""Tools that read the stored responses of the requests of the context."""

from typing import Annotated, Any

from mcp.server.mcpserver import Context, MCPServer
from pydantic import Field

from automata_mcp.core.errors import CoreNotFound
from automata_mcp.services.bodies import read_body, read_described_body, resolve_max_bytes
from automata_mcp.services.raw_messages import raw_view
from automata_mcp.state import AppState, get_state
from automata_mcp.tools.common import (
    READ_ONLY,
    BodyMaxBytes,
    BodyOffset,
    ContextUuid,
    RequestId,
    parse_context_uuid,
    with_hint,
)

NO_RESPONSE_HINT = "If the request exists, it has no stored response."


def register(mcp: MCPServer[AppState]) -> None:
    @mcp.tool(annotations=READ_ONLY)
    async def get_response(
        context_uuid: ContextUuid,
        request_id: RequestId,
        ctx: Context[AppState, Any],
        include_body: Annotated[
            bool, Field(description="Also return the body when it is text.")
        ] = True,
        body_max_bytes: BodyMaxBytes = None,
    ) -> dict[str, Any]:
        """Returns the stored response of a request: its status code, headers and body, in the
        same shape as get_request.

        `body` says how the body is stored, its contentType and size and, when it is text, its
        `text` (up to the server's limit or `body_max_bytes`; `truncated` and `nextOffset` say how
        to read on with get_response_body). Scripts, HTML and XML are read this way too. A binary
        body is only described. A request without a stored response answers "not found".
        """
        state = get_state(ctx)
        uuid = parse_context_uuid(context_uuid)
        try:
            metadata = await state.core.get_response_metadata(uuid, request_id)
        except CoreNotFound as error:
            raise with_hint(error, NO_RESPONSE_HINT) from error
        output = metadata.to_output()
        if include_body:
            settings = state.settings
            body = await read_described_body(
                lambda: state.core.stream_response_body(uuid, request_id),
                metadata.body,
                max_bytes=resolve_max_bytes(
                    body_max_bytes, settings.body_max_bytes, settings.body_max_fetch_bytes
                ),
                fetch_cap=settings.body_max_fetch_bytes,
            )
            output["body"] = output["body"] | body
        return output

    @mcp.tool(annotations=READ_ONLY)
    async def get_response_raw(
        context_uuid: ContextUuid, request_id: RequestId, ctx: Context[AppState, Any]
    ) -> dict[str, Any]:
        """Returns the exact message of a stored response: `headText` (status line, headers and
        the blank line, with CRLF) and `bodyText` when the body is text.

        A body over the server's limit is cut and `notes` says how to read it in parts with
        get_response_body; a binary body is not shown. A request without a stored response
        answers "not found".
        """
        state = get_state(ctx)
        uuid = parse_context_uuid(context_uuid)
        try:
            raw = await state.core.get_response_raw(uuid, request_id)
        except CoreNotFound as error:
            raise with_hint(error, NO_RESPONSE_HINT) from error
        return await raw_view(
            head_b64=raw.raw_head_base64,
            body_b64=raw.raw_body_base64,
            stored_as_file=raw.body_stored_as_file,
            open_body=lambda: state.core.stream_response_body(uuid, request_id),
            view_cap=state.settings.body_max_bytes,
            fetch_cap=state.settings.body_max_fetch_bytes,
            request=False,
        )

    @mcp.tool(annotations=READ_ONLY)
    async def get_response_body(
        context_uuid: ContextUuid,
        request_id: RequestId,
        ctx: Context[AppState, Any],
        offset: BodyOffset = 0,
        max_bytes: BodyMaxBytes = None,
    ) -> dict[str, Any]:
        """Reads the body of a stored response in parts: `max_bytes` bytes from `offset`.

        Use it to read JavaScript, HTML or XML responses (stored as files) and any body longer
        than get_response returns. Returns `text`, `encoding`, `offset`, `returnedBytes`,
        `totalBytes` and `truncated`; when truncated, `nextOffset` is where to read on. A binary
        body is only described. The server reads at most a fixed number of bytes of any body;
        `note` says when the rest cannot be reached. A response without a body answers "not found".
        """
        state = get_state(ctx)
        settings = state.settings
        try:
            return await read_body(
                state.core.stream_response_body(parse_context_uuid(context_uuid), request_id),
                offset=offset,
                max_bytes=resolve_max_bytes(
                    max_bytes, settings.body_max_bytes, settings.body_max_fetch_bytes
                ),
                fetch_cap=settings.body_max_fetch_bytes,
            )
        except CoreNotFound as error:
            raise with_hint(
                error,
                "The request may have no stored response, or the response may have no body "
                "(get_response shows body.storage).",
            ) from error
