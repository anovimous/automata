"""Adding a request the agent observed, and exporting the body property names of the host.

The core parses the messages and enforces every rule (unknown host, tenant of another host, a
message that cannot be parsed). Here the only work is putting the agent's text or base64 into the
form the core expects, and checking that one form of each message was given.
"""

from typing import Any
from uuid import UUID

from mcp.server.mcpserver.exceptions import ToolError

from automata_mcp.core.client import CoreClient
from automata_mcp.core.models.requests import AiRequestAddition
from automata_mcp.services.raw_messages import normalize_raw_message, to_base64

NOT_IN_A_CONTEXT = (
    "This request is not part of any context: you cannot read it back or create jobs on it until "
    "a human adds it to a context."
)


def encode_message(
    text: str | None, base64_value: str | None, *, text_name: str, base64_name: str, required: bool
) -> str | None:
    """The base64 of a message given as text or as base64 (never both).

    Text goes through ``normalize_raw_message`` (CRLF head, closing blank line, body untouched).
    A base64 value is passed on as it is: the core decodes and parses it.
    """
    if text is not None and base64_value is not None:
        raise ToolError(f"Give {text_name} or {base64_name}, not both.")
    if text is None and base64_value is None:
        if required:
            raise ToolError(f"Give the message: {text_name} (text) or {base64_name}.")
        return None
    if text is not None:
        return to_base64(normalize_raw_message(text))
    return base64_value


async def add_request(
    core: CoreClient,
    *,
    host: str,
    raw_request: str | None,
    raw_request_base64: str | None,
    raw_response: str | None,
    raw_response_base64: str | None,
    tenant_id: int | None,
    context_uuid: UUID | None,
) -> dict[str, Any]:
    """Store the request (and response) for the host; return the core's summary and the note."""
    request = encode_message(
        raw_request,
        raw_request_base64,
        text_name="raw_request",
        base64_name="raw_request_base64",
        required=True,
    )
    response = encode_message(
        raw_response,
        raw_response_base64,
        text_name="raw_response",
        base64_name="raw_response_base64",
        required=False,
    )
    assert request is not None  # `required=True` raised otherwise
    addition = AiRequestAddition(
        request_base64=request,
        host=host,
        base64_response=response,
        tenant_id=tenant_id,
        agent_context_uuid=str(context_uuid) if context_uuid else None,
    )
    summary = await core.add_request(addition)
    return summary.to_output() | {"note": NOT_IN_A_CONTEXT}


async def body_property_names(
    core: CoreClient, context_uuid: UUID, max_names: int
) -> dict[str, Any]:
    """The names of the JSON and form fields seen on the context's host, at most ``max_names``.

    The host is the context's own (``get_host``); only its id is sent, never a program id.
    """
    host = await core.get_host(context_uuid)
    names = await core.export_body_property_names(host.id)
    return {"names": names[:max_names], "truncated": len(names) > max_names, "total": len(names)}
