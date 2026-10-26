"""Reading the body of a request or response without ever returning more than the caps allow.

A body is streamed from the core, at most ``fetch_cap`` bytes are read, ``offset`` bytes are skipped
and ``max_bytes`` are kept. Text is decoded with the charset of its ``Content-Type`` (UTF-8 when
there is none); binary content is never returned, only described (plan section 9.2).
"""

import codecs
import re
from collections.abc import Callable
from contextlib import AbstractAsyncContextManager
from dataclasses import dataclass
from typing import Any

import httpx
from mcp.server.mcpserver.exceptions import ToolError

from automata_mcp.core.models.enums import BodyStorage
from automata_mcp.core.models.requests import BodyMetadata

type Stream = AbstractAsyncContextManager[httpx.Response]

TEXT_FAMILIES = frozenset({"JSON", "FORM", "XML", "HTML", "JS", "CSS", "TEXT"})
BINARY_FAMILIES = frozenset({"IMAGE", "AUDIO", "VIDEO", "PDF", "ARCHIVE"})
TEXT_MEDIA_TYPES = frozenset(
    {
        "application/json",
        "application/xml",
        "application/javascript",
        "application/x-javascript",
        "application/ecmascript",
        "application/x-www-form-urlencoded",
    }
)
SNIFF_BYTES = 1024
_CHARSET = re.compile(r"charset\s*=\s*\"?([^;\"\s]+)", re.IGNORECASE)


def media_type(content_type: str | None) -> str:
    """The ``type/subtype`` of a Content-Type header, lower case ('' when there is none)."""
    return (content_type or "").split(";", 1)[0].strip().lower()


def classify(content_type: str | None, mime_family: str | None = None) -> bool | None:
    """Is a body text (True), binary (False), or can only its bytes tell (None)?

    The family the core gives wins; otherwise the Content-Type. Multipart, ``octet-stream`` (what
    the core sends when the body had no usable Content-Type) and anything unknown are left to
    ``looks_binary``.
    """
    family = (mime_family or "").upper()
    if family in TEXT_FAMILIES:
        return True
    if family in BINARY_FAMILIES:
        return False
    kind = media_type(content_type)
    if kind.startswith("text/") or kind in TEXT_MEDIA_TYPES or kind.endswith(("+json", "+xml")):
        return True
    if kind.startswith(("image/", "audio/", "video/")) or kind in {
        "application/pdf",
        "application/zip",
        "application/gzip",
        "application/x-tar",
    }:
        return False
    return None


def looks_binary(sample: bytes) -> bool:
    """A NUL byte in the first KiB means binary."""
    return b"\x00" in sample[:SNIFF_BYTES]


def charset_of(content_type: str | None) -> str:
    """The Python codec name for the charset of a Content-Type (UTF-8 if absent or unknown)."""
    match = _CHARSET.search(content_type or "")
    if match:
        try:
            return codecs.lookup(match.group(1)).name
        except LookupError:
            pass
    return "utf-8"


def decode_text(data: bytes, encoding: str, truncated: bool) -> tuple[str, int]:
    """Decode ``data``; return the text and how many bytes it stands for.

    When ``truncated`` the data was cut anywhere, possibly inside a multi-byte character: those
    trailing bytes are not decoded (they are not counted), so continuing at the next offset
    starts at a character boundary. Otherwise an incomplete character becomes U+FFFD.
    """
    decoder = codecs.getincrementaldecoder(encoding)(errors="replace")
    text = decoder.decode(data, final=not truncated)
    pending = len(decoder.getstate()[0]) if truncated else 0
    return text, len(data) - pending


def resolve_max_bytes(requested: int | None, default: int, fetch_cap: int) -> int:
    """How many bytes to keep: what was asked for, else the default, never above the fetch cap."""
    return min(requested if requested is not None else default, fetch_cap)


@dataclass(frozen=True)
class Slice:
    data: bytes
    offset: int
    truncated: bool  # more bytes exist after ``data``
    total_bytes: int | None


async def read_slice(
    response: httpx.Response, *, offset: int, max_bytes: int, fetch_cap: int
) -> Slice:
    """Read ``max_bytes`` of the body from ``offset``, never reading past ``fetch_cap`` bytes."""
    if offset >= fetch_cap:
        raise ToolError(
            f"offset {offset} is beyond the {fetch_cap} bytes of a body that this server reads; "
            "the rest of the body cannot be reached through this tool."
        )
    end = offset + min(max_bytes, fetch_cap - offset)
    kept: list[bytes] = []
    position = 0
    more = False
    async for chunk in response.aiter_bytes():
        start, stop = position, position + len(chunk)
        if stop > offset and start < end:
            kept.append(chunk[max(offset - start, 0) : min(end, stop) - start])
        position = stop
        if position > end:
            more = True
            break
    total: int | None = None
    if not more:
        total = position
    elif "content-encoding" not in response.headers:
        length = response.headers.get("content-length", "")
        total = int(length) if length.isdigit() else None
    return Slice(b"".join(kept), offset, more, total)


def text_section(slice_: Slice, content_type: str | None, fetch_cap: int) -> dict[str, Any]:
    """A slice of a text body as the agent sees it."""
    encoding = charset_of(content_type)
    text, used = decode_text(slice_.data, encoding, slice_.truncated)
    section: dict[str, Any] = {
        "text": text,
        "encoding": encoding,
        "offset": slice_.offset,
        "returnedBytes": used,
        "truncated": slice_.truncated,
    }
    if slice_.total_bytes is not None:
        section["totalBytes"] = slice_.total_bytes
    if slice_.offset > 0 and slice_.total_bytes is not None and slice_.offset >= slice_.total_bytes:
        section["note"] = (
            f"The offset is at or beyond the end of the body ({slice_.total_bytes} bytes)."
        )
    if slice_.truncated:
        next_offset = slice_.offset + used
        section["nextOffset"] = next_offset
        if next_offset >= fetch_cap:
            section["note"] = (
                f"Truncated. This server reads at most {fetch_cap} bytes of a body, so the rest "
                "cannot be reached."
            )
        else:
            section["note"] = f"Truncated. Call again with offset={next_offset} to read on."
    return section


def binary_section(content_type: str | None, size: int | None) -> dict[str, Any]:
    """A binary body is described, never returned."""
    section: dict[str, Any] = {"binary": True}
    if content_type:
        section["contentType"] = content_type
    if size is not None:
        section["sizeBytes"] = size
    return section


async def read_body(
    stream: Stream,
    *,
    offset: int,
    max_bytes: int,
    fetch_cap: int,
    mime_family: str | None = None,
    known_size: int | None = None,
) -> dict[str, Any]:
    """Open a body stream and return its text (a slice) or a description when it is binary."""
    async with stream as response:
        content_type = response.headers.get("content-type")
        verdict = classify(content_type, mime_family)
        length = response.headers.get("content-length", "")
        size = known_size if known_size is not None else int(length) if length.isdigit() else None
        if verdict is False:
            return binary_section(content_type, size)
        slice_ = await read_slice(response, offset=offset, max_bytes=max_bytes, fetch_cap=fetch_cap)
    if verdict is None and looks_binary(slice_.data):
        return binary_section(content_type, size)
    return text_section(slice_, content_type, fetch_cap)


async def read_described_body(
    open_stream: Callable[[], Stream],
    body: BodyMetadata,
    *,
    max_bytes: int,
    fetch_cap: int,
) -> dict[str, Any]:
    """The body of a request or response whose metadata is known.

    No stream is opened when the metadata already says there is no body or that it is binary.
    """
    if body.storage == BodyStorage.NONE:
        return {"hasBody": False}
    if classify(body.content_type, body.mime_family) is False:
        return binary_section(body.content_type, body.size_bytes)
    return await read_body(
        open_stream(),
        offset=0,
        max_bytes=max_bytes,
        fetch_cap=fetch_cap,
        mime_family=body.mime_family,
        known_size=body.size_bytes,
    )
