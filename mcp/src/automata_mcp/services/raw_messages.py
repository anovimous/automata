"""Raw HTTP messages: text <-> bytes <-> base64, and the raw views of a request or response.

The core stores and takes messages as base64 of the exact bytes (start line, headers, a blank line,
body). This module is format translation only; it validates nothing about the message itself.
"""

import base64
import binascii
import re
from collections.abc import Callable
from typing import Any

from automata_mcp.core.errors import CoreResponseError
from automata_mcp.services.bodies import (
    Slice,
    charset_of,
    classify,
    decode_text,
    looks_binary,
    read_slice,
)
from automata_mcp.services.bodies import Stream as BodyStream

_BLANK_LINE = re.compile(r"\r?\n\r?\n")
_LINE_END = re.compile(r"\r?\n")
HEAD_ENCODING = "iso-8859-1"


def normalize_raw_message(text: str) -> bytes:
    """The bytes of a raw HTTP message written as text.

    The head (up to the first blank line, ``\\n\\n`` or ``\\r\\n\\r\\n``) gets CRLF line endings and
    ends with a blank line; the body after it is kept unchanged. A text without a blank line is
    all head. ``Content-Length`` is not recomputed.
    """
    match = _BLANK_LINE.search(text)
    if match:
        head, body = text[: match.start()], text[match.end() :]
    else:
        head, body = text.rstrip("\r\n"), ""
    head = _LINE_END.sub("\r\n", head) + "\r\n\r\n"
    return head.encode("utf-8") + body.encode("utf-8")


def to_base64(data: bytes) -> str:
    return base64.b64encode(data).decode("ascii")


def from_base64(value: str) -> bytes:
    """Bytes of a base64 payload of the core; a payload that is not base64 is a response error."""
    try:
        return base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError):
        raise CoreResponseError("The core sent a message that is not valid base64.") from None


def compose_message(head_b64: str, body: str | bytes | None = None) -> bytes:
    """Head and body joined into the bytes of a message (a ``str`` body is base64)."""
    if body is None:
        raw_body = b""
    elif isinstance(body, str):
        raw_body = from_base64(body)
    else:
        raw_body = body
    return from_base64(head_b64) + raw_body


def header_value(head_text: str, name: str) -> str | None:
    """The value of a header in the text of a message head (case-insensitive), or None."""
    wanted = name.lower()
    for line in head_text.split("\r\n")[1:]:
        if not line.strip():
            break
        key, separator, value = line.partition(":")
        if separator and key.strip().lower() == wanted:
            return value.strip()
    return None


def decode_head(head_b64: str) -> str:
    """The head of a message as text (ISO-8859-1: every byte maps to a character)."""
    return from_base64(head_b64).decode(HEAD_ENCODING)


async def raw_view(
    *,
    head_b64: str,
    body_b64: str | None,
    stored_as_file: bool,
    open_body: Callable[[], BodyStream] | None,
    view_cap: int,
    fetch_cap: int,
    request: bool,
) -> dict[str, Any]:
    """The raw message for the agent.

    ``headText`` is always there. The body is included when it is at most ``view_cap`` bytes
    (``bodyText`` when it is text); a bigger one is cut and the agent is sent to the body tools.
    For a request the exact bytes are also given as ``rawRequestBase64`` and, for a text body,
    the whole message as ``rawRequestText``. A body stored as a file is read through ``open_body``
    (at most ``fetch_cap`` bytes; ``open_body`` opens that stream).
    """
    head = from_base64(head_b64)
    head_text = head.decode(HEAD_ENCODING)
    content_type = header_value(head_text, "content-type")
    notes: list[str] = []
    view: dict[str, Any] = {"headText": head_text, "bodyStoredAsFile": stored_as_file}

    body: bytes | None = None
    complete = True
    if body_b64 is not None:
        body = from_base64(body_b64)
    elif not stored_as_file:
        body = b""
    elif open_body is not None:
        async with open_body() as response:
            slice_: Slice = await read_slice(
                response, offset=0, max_bytes=fetch_cap, fetch_cap=fetch_cap
            )
        body, complete = slice_.data, not slice_.truncated
        if not complete:
            notes.append(
                f"The body is a file of more than {fetch_cap} bytes, which this server does not "
                "read whole: it is not included. Read it in parts with the body tool."
            )
            body = None

    if body is not None and len(body) > view_cap:
        notes.append(
            f"The body is {len(body)} bytes, over the {view_cap} this view returns: only the start "
            "is shown as bodyText. Read it in parts with the body tool (offset, max_bytes)."
        )
        view["bodyTruncated"] = True
        text, _ = decode_text(body[:view_cap], charset_of(content_type), truncated=True)
        if classify(content_type) is not False and not looks_binary(body[:view_cap]):
            view["bodyText"] = text
    elif body is not None:
        textual = classify(content_type)
        if textual is None:
            textual = not looks_binary(body)
        if body and textual:
            view["bodyText"] = decode_text(body, charset_of(content_type), truncated=False)[0]
        elif body:
            notes.append("The body is binary: bodyText is omitted.")
        if request:
            view["rawRequestBase64"] = to_base64(head + body)
            if textual or not body:
                view["rawRequestText"] = head_text + view.get("bodyText", "")
    if notes:
        view["notes"] = notes
    return view
