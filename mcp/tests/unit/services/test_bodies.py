import itertools
from typing import Any

import httpx
import pytest
from mcp.server.mcpserver.exceptions import ToolError

from automata_mcp.core.models.enums import BodyStorage
from automata_mcp.core.models.requests import BodyMetadata
from automata_mcp.services.bodies import (
    Slice,
    binary_section,
    charset_of,
    classify,
    decode_text,
    looks_binary,
    media_type,
    read_body,
    read_described_body,
    read_slice,
    resolve_max_bytes,
    text_section,
)
from helpers import chunked_response, stream_of

DIGITS = b"0123456789"


# ---------------------------------------------------------------------------- classification


@pytest.mark.parametrize(
    ("content_type", "expected"),
    [
        ("application/json", "application/json"),
        ("Application/JSON; charset=utf-8", "application/json"),
        ("  text/html ;q=1", "text/html"),
        ("", ""),
        (None, ""),
    ],
)
def test_media_type(content_type: str | None, expected: str) -> None:
    assert media_type(content_type) == expected


@pytest.mark.parametrize("family", ["JSON", "FORM", "XML", "HTML", "JS", "CSS", "TEXT", "json"])
def test_text_families_are_text(family: str) -> None:
    assert classify("application/octet-stream", family) is True


@pytest.mark.parametrize("family", ["IMAGE", "AUDIO", "VIDEO", "PDF", "ARCHIVE"])
def test_binary_families_are_binary(family: str) -> None:
    assert classify("text/plain", family) is False


@pytest.mark.parametrize(
    "content_type",
    [
        "text/plain",
        "text/html; charset=utf-8",
        "application/json",
        "application/ld+json",
        "application/atom+xml",
        "application/xml",
        "application/javascript",
        "application/x-www-form-urlencoded",
    ],
)
def test_text_content_types_are_text(content_type: str) -> None:
    assert classify(content_type) is True


@pytest.mark.parametrize(
    "content_type", ["image/png", "audio/mpeg", "video/mp4", "application/pdf", "application/zip"]
)
def test_binary_content_types_are_binary(content_type: str) -> None:
    assert classify(content_type) is False


@pytest.mark.parametrize(
    ("content_type", "family"),
    [
        ("application/octet-stream", None),
        ("application/octet-stream", "OCTET_STREAM"),
        ("multipart/form-data; boundary=x", "MULTIPART"),
        (None, None),
        ("application/x-weird", "OTHER"),
    ],
)
def test_everything_else_is_left_to_the_bytes(content_type: str | None, family: str | None) -> None:
    assert classify(content_type, family) is None


def test_looks_binary_only_looks_at_the_first_kilobyte() -> None:
    assert looks_binary(b"abc\x00def") is True
    assert looks_binary(b"plain text") is False
    assert looks_binary(b"a" * 1024 + b"\x00") is False
    assert looks_binary(b"") is False


# ---------------------------------------------------------------------------- text


@pytest.mark.parametrize(
    ("content_type", "expected"),
    [
        (None, "utf-8"),
        ("text/html", "utf-8"),
        ("text/html; charset=UTF-8", "utf-8"),
        ('text/html; charset="iso-8859-1"', "iso8859-1"),
        ("text/plain;charset=latin1", "iso8859-1"),
        ("text/plain; charset=no-such-charset", "utf-8"),
    ],
)
def test_charset_of(content_type: str | None, expected: str) -> None:
    assert charset_of(content_type) == expected


def test_decode_text_whole() -> None:
    assert decode_text("héllo".encode(), "utf-8", truncated=False) == ("héllo", 6)


def test_decode_text_does_not_cut_a_character_when_truncated() -> None:
    euro = "€".encode()  # three bytes

    text, used = decode_text(b"ab" + euro[:2], "utf-8", truncated=True)

    assert (text, used) == ("ab", 2)


def test_decode_text_replaces_an_incomplete_character_when_not_truncated() -> None:
    text, used = decode_text(b"ab" + "€".encode()[:2], "utf-8", truncated=False)

    assert text == "ab�"
    assert used == 4


def test_decode_text_with_another_charset() -> None:
    assert decode_text("é".encode("latin-1"), "iso8859-1", truncated=True) == ("é", 1)


def test_decode_text_replaces_invalid_bytes() -> None:
    assert decode_text(b"a\xffb", "utf-8", truncated=False)[0] == "a�b"


@pytest.mark.parametrize(
    ("requested", "expected"), [(None, 100), (50, 50), (500, 200), (200, 200), (1, 1)]
)
def test_resolve_max_bytes(requested: int | None, expected: int) -> None:
    assert resolve_max_bytes(requested, 100, 200) == expected


# ---------------------------------------------------------------------------- read_slice


@pytest.mark.parametrize("splits", [[10], [5, 5], [3, 3, 4], [1] * 10, [4, 0, 6]])
async def test_read_slice_matches_plain_slicing_whatever_the_chunking(splits: list[int]) -> None:
    chunks, position = [], 0
    for size in splits:
        chunks.append(DIGITS[position : position + size])
        position += size

    for offset, max_bytes in itertools.product(range(0, 12), range(1, 13)):
        response = chunked_response(chunks)

        result = await read_slice(response, offset=offset, max_bytes=max_bytes, fetch_cap=1000)

        assert result.data == DIGITS[offset : offset + max_bytes], (offset, max_bytes)
        assert result.truncated == (offset + max_bytes < len(DIGITS)), (offset, max_bytes)
        assert result.offset == offset
        if not result.truncated:
            assert result.total_bytes == len(DIGITS)


async def test_read_slice_uses_the_content_length_when_truncated() -> None:
    response = chunked_response([DIGITS], headers={"Content-Length": "10"})

    result = await read_slice(response, offset=0, max_bytes=4, fetch_cap=1000)

    assert (result.data, result.truncated, result.total_bytes) == (b"0123", True, 10)


async def test_read_slice_does_not_trust_a_content_length_of_an_encoded_body() -> None:
    response = chunked_response(
        [DIGITS], headers={"Content-Length": "3", "Content-Encoding": "identity"}
    )

    result = await read_slice(response, offset=0, max_bytes=4, fetch_cap=1000)

    assert result.total_bytes is None


async def test_read_slice_total_is_unknown_when_truncated_without_content_length() -> None:
    result = await read_slice(chunked_response([DIGITS]), offset=0, max_bytes=4, fetch_cap=1000)

    assert result.total_bytes is None


async def test_read_slice_never_goes_past_the_fetch_cap() -> None:
    result = await read_slice(
        chunked_response([DIGITS, DIGITS]), offset=2, max_bytes=100, fetch_cap=8
    )

    assert result.data == b"234567"
    assert result.truncated is True


async def test_read_slice_stops_reading_once_it_knows_there_is_more() -> None:
    read: list[int] = []

    class Spy(httpx.AsyncByteStream):
        async def __aiter__(self) -> Any:
            for number in range(100):
                read.append(number)
                yield b"x" * 10

    response = httpx.Response(200, stream=Spy())

    await read_slice(response, offset=0, max_bytes=15, fetch_cap=1000)

    assert read == [0, 1]  # the second chunk already crosses the limit


async def test_read_slice_refuses_an_offset_beyond_the_fetch_cap() -> None:
    with pytest.raises(ToolError, match="offset 8 is beyond the 8 bytes"):
        await read_slice(chunked_response([DIGITS]), offset=8, max_bytes=1, fetch_cap=8)


async def test_read_slice_of_an_offset_past_the_end_is_empty() -> None:
    result = await read_slice(chunked_response([DIGITS]), offset=50, max_bytes=5, fetch_cap=1000)

    assert result.data == b""
    assert result.truncated is False
    assert result.total_bytes == 10


# ---------------------------------------------------------------------------- sections


def test_text_section_of_a_whole_body() -> None:
    section = text_section(Slice(b'{"a":1}', 0, False, 7), "application/json", 1000)

    assert section == {
        "text": '{"a":1}',
        "encoding": "utf-8",
        "offset": 0,
        "returnedBytes": 7,
        "truncated": False,
        "totalBytes": 7,
    }


def test_text_section_of_a_truncated_body_says_where_to_read_on() -> None:
    section = text_section(Slice(b"0123", 0, True, 10), "text/plain", 1000)

    assert section["truncated"] is True
    assert section["nextOffset"] == 4
    assert section["note"] == "Truncated. Call again with offset=4 to read on."
    assert section["totalBytes"] == 10


def test_text_section_omits_an_unknown_total() -> None:
    assert "totalBytes" not in text_section(Slice(b"01", 0, True, None), None, 1000)


def test_text_section_says_when_the_cap_makes_the_rest_unreachable() -> None:
    section = text_section(Slice(b"0123", 4, True, None), None, 8)

    assert section["nextOffset"] == 8
    assert "reads at most 8 bytes" in section["note"]


def test_text_section_does_not_count_a_cut_character() -> None:
    section = text_section(Slice(b"ab" + "€".encode()[:2], 0, True, None), None, 1000)

    assert section["text"] == "ab"
    assert section["returnedBytes"] == 2
    assert section["nextOffset"] == 2


def test_text_section_notes_an_offset_past_the_end() -> None:
    section = text_section(Slice(b"", 50, False, 10), None, 1000)

    assert section["note"] == "The offset is at or beyond the end of the body (10 bytes)."


def test_binary_section_describes_without_returning_content() -> None:
    assert binary_section("image/png", 2048) == {
        "binary": True,
        "contentType": "image/png",
        "sizeBytes": 2048,
    }
    assert binary_section(None, None) == {"binary": True}


# ---------------------------------------------------------------------------- read_body


async def test_read_body_returns_the_text_of_a_json_body() -> None:
    response = chunked_response([b'{"a":', b"1}"], headers={"Content-Type": "application/json"})

    section = await read_body(stream_of(response), offset=0, max_bytes=100, fetch_cap=1000)

    assert section["text"] == '{"a":1}'
    assert section["truncated"] is False


async def test_read_body_describes_a_binary_content_type_without_reading_it() -> None:
    class Boom(httpx.AsyncByteStream):
        async def __aiter__(self) -> Any:
            raise AssertionError("the body must not be read")
            yield b""

    response = httpx.Response(
        200, headers={"Content-Type": "image/png", "Content-Length": "2048"}, stream=Boom()
    )

    section = await read_body(stream_of(response), offset=0, max_bytes=100, fetch_cap=1000)

    assert section == {"binary": True, "contentType": "image/png", "sizeBytes": 2048}


async def test_read_body_finds_binary_by_a_nul_byte_when_the_type_says_nothing() -> None:
    response = chunked_response(
        [b"\x89PNG\x00\x01"], headers={"Content-Type": "application/octet-stream"}
    )

    section = await read_body(stream_of(response), offset=0, max_bytes=100, fetch_cap=1000)

    assert section["binary"] is True


async def test_read_body_reads_octet_stream_text() -> None:
    response = chunked_response(
        [b"plain words"], headers={"Content-Type": "application/octet-stream"}
    )

    section = await read_body(stream_of(response), offset=0, max_bytes=100, fetch_cap=1000)

    assert section["text"] == "plain words"


async def test_read_body_prefers_the_family_of_the_core() -> None:
    response = chunked_response([b"x = 1"], headers={"Content-Type": "application/octet-stream"})

    section = await read_body(
        stream_of(response), offset=0, max_bytes=100, fetch_cap=1000, mime_family="JS"
    )

    assert section["text"] == "x = 1"


async def test_read_body_uses_the_charset_of_the_response() -> None:
    response = chunked_response(
        ["café".encode("latin-1")], headers={"Content-Type": "text/plain; charset=iso-8859-1"}
    )

    section = await read_body(stream_of(response), offset=0, max_bytes=100, fetch_cap=1000)

    assert section["text"] == "café"
    assert section["encoding"] == "iso8859-1"


async def test_read_body_honours_offset_and_max_bytes() -> None:
    response = chunked_response([DIGITS], headers={"Content-Type": "text/plain"})

    section = await read_body(stream_of(response), offset=2, max_bytes=3, fetch_cap=1000)

    assert section["text"] == "234"
    assert section["nextOffset"] == 5


# ---------------------------------------------------------------------------- read_described_body


def body(
    storage: BodyStorage, content_type: str | None, family: str | None, size: int = 5
) -> BodyMetadata:
    return BodyMetadata(
        storage=storage, content_type=content_type, mime_family=family, size_bytes=size
    )


def must_not_open() -> Any:
    raise AssertionError("no stream must be opened")


async def test_no_body_opens_no_stream() -> None:
    result = await read_described_body(
        must_not_open, body(BodyStorage.NONE, None, None, 0), max_bytes=10, fetch_cap=100
    )

    assert result == {"hasBody": False}


async def test_a_binary_body_known_from_the_metadata_opens_no_stream() -> None:
    result = await read_described_body(
        must_not_open,
        body(BodyStorage.FILE, "image/png", "IMAGE", 2048),
        max_bytes=10,
        fetch_cap=100,
    )

    assert result == {"binary": True, "contentType": "image/png", "sizeBytes": 2048}


async def test_a_text_body_is_read_with_the_known_size() -> None:
    response = chunked_response([b"hello"], headers={"Content-Type": "text/plain"})

    result = await read_described_body(
        lambda: stream_of(response),
        body(BodyStorage.INLINE, "text/plain", "TEXT"),
        max_bytes=3,
        fetch_cap=100,
    )

    assert result["text"] == "hel"
    assert result["truncated"] is True
    assert result["nextOffset"] == 3


async def test_an_ambiguous_body_that_turns_out_binary_keeps_the_size_of_the_metadata() -> None:
    response = chunked_response(
        [b"\x00\x01\x02"], headers={"Content-Type": "application/octet-stream"}
    )

    result = await read_described_body(
        lambda: stream_of(response),
        body(BodyStorage.FILE, "application/octet-stream", "OCTET_STREAM", 3),
        max_bytes=10,
        fetch_cap=100,
    )

    assert result == {"binary": True, "contentType": "application/octet-stream", "sizeBytes": 3}
