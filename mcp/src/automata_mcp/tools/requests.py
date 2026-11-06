"""Tools that read the requests of the context."""

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
    PageNumber,
    RequestId,
    page_output,
    parse_context_uuid,
    with_hint,
)

NO_BODY_HINT = "If the request exists, it may simply have no body (get_request shows body.storage)."


def register(mcp: MCPServer[AppState]) -> None:
    @mcp.tool(annotations=READ_ONLY)
    async def list_context_requests(
        context_uuid: ContextUuid,
        ctx: Context[AppState, Any],
        equality_set_id: Annotated[
            int | None,
            Field(description="Only the members of this equality set item of the context."),
        ] = None,
        page: PageNumber = 0,
        size: Annotated[int, Field(description="Items per page, 1 to 100.")] = 20,
    ) -> dict[str, Any]:
        """Lists every request you can read in the context, newest first: each request item and
        every member of an equality set item, with its method, computatedPath, equalitySetId,
        tenantId, `resolvedTarget` and `automataJobEligible`.

        list_targets shows only the requests you can create jobs on. Use this one to find the
        older members of an equality set (pass `equality_set_id`, the equalitySetId of one of the
        context's items, see get_context) and read them with get_request. A request with
        `resolvedTarget` false is readable but cannot be a job target. Results are paged:
        `nextPage` says which page to ask for next.
        """
        state = get_state(ctx)
        try:
            result = await state.core.list_context_requests(
                parse_context_uuid(context_uuid),
                equality_set_id=equality_set_id,
                page=page,
                size=size,
            )
        except CoreNotFound as error:
            if equality_set_id is None:
                raise
            raise with_hint(
                error,
                "equality_set_id must be the equalitySetId of an equality set item of your "
                "context (get_context lists the items).",
            ) from error
        return page_output(result, "requests")

    @mcp.tool(annotations=READ_ONLY)
    async def get_request(
        context_uuid: ContextUuid,
        request_id: RequestId,
        ctx: Context[AppState, Any],
        include_body: Annotated[
            bool, Field(description="Also return the body when it is text.")
        ] = True,
        body_max_bytes: BodyMaxBytes = None,
    ) -> dict[str, Any]:
        """Returns a request: its line (method, computatedPath, target as sent, version), its
        headers, the tenant and response ids, and its body.

        This is the first tool to understand a request. `body` says how the body is stored
        (storage NONE, INLINE or FILE), its contentType and size, and, when it is text, its `text`
        (up to the server's limit or `body_max_bytes`; `truncated` and `nextOffset` say how to
        read on with get_request_body). A binary body is only described (`binary`: true): its
        bytes are never returned. Use get_request_raw for the exact bytes of the message.
        """
        state = get_state(ctx)
        uuid = parse_context_uuid(context_uuid)
        metadata = await state.core.get_request_metadata(uuid, request_id)
        output = metadata.to_output()
        if include_body:
            settings = state.settings
            body = await read_described_body(
                lambda: state.core.stream_request_body(uuid, request_id),
                metadata.body,
                max_bytes=resolve_max_bytes(
                    body_max_bytes, settings.body_max_bytes, settings.body_max_fetch_bytes
                ),
                fetch_cap=settings.body_max_fetch_bytes,
            )
            output["body"] = output["body"] | body
        return output

    @mcp.tool(annotations=READ_ONLY)
    async def get_request_raw(
        context_uuid: ContextUuid, request_id: RequestId, ctx: Context[AppState, Any]
    ) -> dict[str, Any]:
        """Returns the exact message of a request: `headText` (start line, headers and the blank
        line, with CRLF), `bodyText` when the body is text, `rawRequestText` (head and body as one
        text) and `rawRequestBase64` (the exact bytes).

        Use this when you need the exact bytes of the request, for example to build the
        `rawRequestBase64` of a `fuzz` job's `custom_config`: put the FUZZ keyword where the
        wordlist entries go. A body over the server's limit is cut and `notes` says how to read
        it in parts with get_request_body; a binary body is not shown as text, but
        `rawRequestBase64` still has its bytes.
        """
        state = get_state(ctx)
        uuid = parse_context_uuid(context_uuid)
        raw = await state.core.get_request_raw(uuid, request_id)
        view = await raw_view(
            head_b64=raw.raw_head_base64,
            body_b64=raw.raw_body_base64,
            stored_as_file=raw.body_stored_as_file,
            open_body=lambda: state.core.stream_request_body(uuid, request_id),
            view_cap=state.settings.body_max_bytes,
            fetch_cap=state.settings.body_max_fetch_bytes,
            request=True,
        )
        if raw.response_id is not None:
            view["responseId"] = raw.response_id
        return view

    @mcp.tool(annotations=READ_ONLY)
    async def get_request_body(
        context_uuid: ContextUuid,
        request_id: RequestId,
        ctx: Context[AppState, Any],
        offset: BodyOffset = 0,
        max_bytes: BodyMaxBytes = None,
    ) -> dict[str, Any]:
        """Reads the body of a request in parts: `max_bytes` bytes from `offset`.

        Returns `text`, `encoding`, `offset`, `returnedBytes`, `totalBytes` and `truncated`; when
        truncated, `nextOffset` is where to read on. Use it for large bodies (get_request returns
        only the start) or multipart ones. A binary body is only described. The server reads at
        most a fixed number of bytes of any body; `note` says when the rest cannot be reached.
        A request without a body answers "not found".
        """
        state = get_state(ctx)
        settings = state.settings
        try:
            return await read_body(
                state.core.stream_request_body(parse_context_uuid(context_uuid), request_id),
                offset=offset,
                max_bytes=resolve_max_bytes(
                    max_bytes, settings.body_max_bytes, settings.body_max_fetch_bytes
                ),
                fetch_cap=settings.body_max_fetch_bytes,
            )
        except CoreNotFound as error:
            raise with_hint(error, NO_BODY_HINT) from error
