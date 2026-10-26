import base64
from typing import Any

import pytest

from automata_mcp.core.errors import CoreResponseError
from automata_mcp.services.raw_messages import (
    compose_message,
    decode_head,
    from_base64,
    header_value,
    normalize_raw_message,
    raw_view,
    to_base64,
)
from helpers import chunked_response, stream_of

HEAD = (
    "POST /api/users/42 HTTP/1.1\r\nHost: api.example.com\r\nContent-Type: application/json\r\n\r\n"
)
HEAD_B64 = base64.b64encode(HEAD.encode()).decode()
BODY = '{"u":"a"}'
BODY_B64 = base64.b64encode(BODY.encode()).decode()


# ---------------------------------------------------------------------------- normalize_raw_message


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("GET / HTTP/1.1\nHost: x\n\n", b"GET / HTTP/1.1\r\nHost: x\r\n\r\n"),
        ("GET / HTTP/1.1\r\nHost: x\r\n\r\n", b"GET / HTTP/1.1\r\nHost: x\r\n\r\n"),
        ("GET / HTTP/1.1\nHost: x", b"GET / HTTP/1.1\r\nHost: x\r\n\r\n"),
        ("GET / HTTP/1.1\nHost: x\n", b"GET / HTTP/1.1\r\nHost: x\r\n\r\n"),
        ("GET / HTTP/1.1\r\nHost: x\n\r\n", b"GET / HTTP/1.1\r\nHost: x\r\n\r\n"),
        ("GET / HTTP/1.1", b"GET / HTTP/1.1\r\n\r\n"),
    ],
)
def test_the_head_gets_crlf_and_a_closing_blank_line(text: str, expected: bytes) -> None:
    assert normalize_raw_message(text) == expected


def test_the_body_is_kept_unchanged() -> None:
    text = 'POST /x HTTP/1.1\nHost: x\n\n{"a":\n  "b"}\n'

    assert normalize_raw_message(text) == (b'POST /x HTTP/1.1\r\nHost: x\r\n\r\n{"a":\n  "b"}\n')


def test_a_body_with_its_own_blank_lines_is_not_split_again() -> None:
    result = normalize_raw_message("POST / HTTP/1.1\nHost: x\n\nline1\n\nline2\r\n\r\nline3")

    assert result.endswith(b"\r\n\r\nline1\n\nline2\r\n\r\nline3")
    assert result.count(b"Host: x\r\n\r\n") == 1


def test_the_keyword_for_fuzzing_is_kept() -> None:
    assert b"/api/users/FUZZ" in normalize_raw_message("GET /api/users/FUZZ HTTP/1.1\nHost: x\n\n")


def test_non_ascii_text_is_encoded_as_utf8() -> None:
    assert normalize_raw_message("POST / HTTP/1.1\n\n€") == "POST / HTTP/1.1\r\n\r\n€".encode()


def test_content_length_is_not_recomputed() -> None:
    result = normalize_raw_message("POST / HTTP/1.1\nContent-Length: 99\n\nab")

    assert b"Content-Length: 99" in result


# ---------------------------------------------------------------------------- base64 and compose


def test_base64_round_trip() -> None:
    assert from_base64(to_base64(b"\x00\xffabc")) == b"\x00\xffabc"


def test_invalid_base64_is_a_response_error() -> None:
    with pytest.raises(CoreResponseError, match="not valid base64"):
        from_base64("***")


def test_compose_message_joins_head_and_body() -> None:
    assert compose_message(HEAD_B64, BODY_B64) == (HEAD + BODY).encode()
    assert compose_message(HEAD_B64, BODY.encode()) == (HEAD + BODY).encode()
    assert compose_message(HEAD_B64, None) == HEAD.encode()
    assert compose_message(HEAD_B64) == HEAD.encode()


def test_compose_message_rejects_a_bad_body() -> None:
    with pytest.raises(CoreResponseError):
        compose_message(HEAD_B64, "***")


def test_decode_head_reads_every_byte() -> None:
    head = base64.b64encode(b"GET / HTTP/1.1\r\nX: \xe9\r\n\r\n").decode()

    assert decode_head(head) == "GET / HTTP/1.1\r\nX: é\r\n\r\n"


@pytest.mark.parametrize(
    ("name", "expected"),
    [
        ("content-type", "application/json"),
        ("Content-Type", "application/json"),
        ("HOST", "api.example.com"),
        ("missing", None),
    ],
)
def test_header_value(name: str, expected: str | None) -> None:
    assert header_value(HEAD, name) == expected


def test_header_value_ignores_the_start_line_and_stops_at_the_blank_line() -> None:
    head = "GET /a:b HTTP/1.1\r\nHost: x\r\n\r\nX-Late: 1\r\n"

    assert header_value(head, "get /a") is None
    assert header_value(head, "x-late") is None


def test_header_value_keeps_colons_in_the_value() -> None:
    assert (
        header_value("GET / HTTP/1.1\r\nReferer: http://x:80/\r\n\r\n", "referer") == "http://x:80/"
    )


# ---------------------------------------------------------------------------- raw_view


async def view(
    *,
    head: bytes = HEAD.encode(),
    body: bytes | None = BODY.encode(),
    stored_as_file: bool = False,
    open_body: Any = None,
    view_cap: int = 1000,
    fetch_cap: int = 10_000,
    request: bool = True,
) -> dict[str, Any]:
    return await raw_view(
        head_b64=to_base64(head),
        body_b64=None if body is None else to_base64(body),
        stored_as_file=stored_as_file,
        open_body=open_body,
        view_cap=view_cap,
        fetch_cap=fetch_cap,
        request=request,
    )


async def test_a_request_with_a_text_body() -> None:
    result = await view()

    assert result == {
        "headText": HEAD,
        "bodyStoredAsFile": False,
        "bodyText": BODY,
        "rawRequestBase64": to_base64((HEAD + BODY).encode()),
        "rawRequestText": HEAD + BODY,
    }


async def test_a_request_without_a_body() -> None:
    result = await view(body=None)

    assert result == {
        "headText": HEAD,
        "bodyStoredAsFile": False,
        "rawRequestBase64": to_base64(HEAD.encode()),
        "rawRequestText": HEAD,
    }


async def test_a_response_has_no_raw_request_forms() -> None:
    result = await view(request=False)

    assert result == {"headText": HEAD, "bodyStoredAsFile": False, "bodyText": BODY}


async def test_a_binary_body_gives_the_bytes_but_no_text() -> None:
    body = b"\x00\x01\x02binary"
    head = b"POST / HTTP/1.1\r\nContent-Type: application/octet-stream\r\n\r\n"

    result = await view(head=head, body=body)

    assert "bodyText" not in result
    assert "rawRequestText" not in result
    assert result["rawRequestBase64"] == to_base64(head + body)
    assert result["notes"] == ["The body is binary: bodyText is omitted."]


async def test_the_charset_of_the_head_decodes_the_body() -> None:
    head = b"POST / HTTP/1.1\r\nContent-Type: text/plain; charset=iso-8859-1\r\n\r\n"

    result = await view(head=head, body="café".encode("latin-1"))

    assert result["bodyText"] == "café"


async def test_a_body_stored_as_a_file_is_read_through_the_stream() -> None:
    response = chunked_response(
        [b'{"big":', b"true}"], headers={"Content-Type": "application/json"}
    )

    result = await view(body=None, stored_as_file=True, open_body=lambda: stream_of(response))

    assert result["bodyStoredAsFile"] is True
    assert result["bodyText"] == '{"big":true}'
    assert result["rawRequestBase64"] == to_base64(HEAD.encode() + b'{"big":true}')


async def test_a_file_over_the_fetch_cap_is_left_out_with_an_explanation() -> None:
    response = chunked_response([b"x" * 50])

    result = await view(
        body=None, stored_as_file=True, open_body=lambda: stream_of(response), fetch_cap=20
    )

    assert "bodyText" not in result
    assert "rawRequestBase64" not in result
    assert "more than 20 bytes" in result["notes"][0]


async def test_a_body_over_the_view_cap_is_cut_and_the_raw_forms_are_omitted() -> None:
    result = await view(body=b"a" * 50, view_cap=10)

    assert result["bodyTruncated"] is True
    assert result["bodyText"] == "a" * 10
    assert "rawRequestBase64" not in result
    assert "rawRequestText" not in result
    assert "over the 10 this view returns" in result["notes"][0]


async def test_a_binary_body_over_the_view_cap_shows_no_text_at_all() -> None:
    result = await view(body=b"\x00" * 50, view_cap=10)

    assert result["bodyTruncated"] is True
    assert "bodyText" not in result


async def test_a_cut_does_not_split_a_multibyte_character() -> None:
    result = await view(body=("€" * 5).encode(), view_cap=7)

    assert result["bodyText"] == "€€"


async def test_a_stored_file_without_a_way_to_open_it_is_just_not_included() -> None:
    result = await view(body=None, stored_as_file=True, open_body=None)

    assert "bodyText" not in result
    assert result["bodyStoredAsFile"] is True


async def test_an_invalid_head_is_a_response_error() -> None:
    with pytest.raises(CoreResponseError):
        await raw_view(
            head_b64="***",
            body_b64=None,
            stored_as_file=False,
            open_body=None,
            view_cap=10,
            fetch_cap=10,
            request=True,
        )
