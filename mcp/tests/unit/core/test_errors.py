import pytest

from automata_mcp.core.errors import (
    MAX_MESSAGE_CHARS,
    CoreBadRequest,
    CoreConflict,
    CoreError,
    CoreNotFound,
    CoreNotImplemented,
    CorePayloadTooLarge,
    CoreRejected,
    CoreServerError,
    CoreUnprocessable,
    CoreUnreachable,
    error_from_response,
    truncate_message,
    unreachable,
)


@pytest.mark.parametrize(
    ("status", "error_type", "guidance"),
    [
        (404, CoreNotFound, "Not found, or not part of this context"),
        (400, CoreBadRequest, "The core rejected the arguments"),
        (409, CoreConflict, "The core refused because of the current state"),
        (413, CorePayloadTooLarge, "512 MB"),
        (422, CoreUnprocessable, "That cannot be a job target"),
        (501, CoreNotImplemented, "Not supported by the core"),
        (500, CoreServerError, "The core failed (HTTP 500)"),
        (503, CoreServerError, "The core failed (HTTP 503)"),
        (415, CoreRejected, "HTTP 415"),
    ],
)
def test_each_status_maps_to_its_error(
    status: int, error_type: type[CoreError], guidance: str
) -> None:
    error = error_from_response(status, "the core said this")

    assert type(error) is error_type
    assert guidance in str(error)
    assert "the core said this" in str(error)
    assert error.status == status
    assert error.core_message == "the core said this"


def test_the_404_hint_points_to_the_listing_tools() -> None:
    assert "list_targets" in str(error_from_response(404, "Request not found"))


def test_the_422_hint_tells_the_agent_to_ask_the_user_about_the_host() -> None:
    assert "tell the user" in str(error_from_response(422, "Host 2 has a rate limit of 0"))


def test_5xx_tells_the_agent_not_to_loop() -> None:
    assert "Do not retry in a loop" in str(error_from_response(500, "boom"))


def test_an_empty_body_gets_a_placeholder() -> None:
    error = error_from_response(404, "   ")

    assert error.core_message == "(no message)"


def test_long_messages_are_cut() -> None:
    error = error_from_response(400, "x" * 5000)

    assert len(error.core_message) == MAX_MESSAGE_CHARS
    assert error.core_message.endswith("…")


def test_truncate_message_strips_and_keeps_short_text() -> None:
    assert truncate_message("  hello \n") == "hello"
    assert truncate_message("abcdef", limit=6) == "abcdef"
    assert truncate_message("abcdefg", limit=6) == "abcde…"


def test_every_error_is_a_core_error() -> None:
    assert isinstance(error_from_response(404, "x"), CoreError)
    assert isinstance(
        unreachable("http://core", "ConnectError", may_have_been_applied=False), CoreError
    )


def test_unreachable_names_the_core_url_and_the_reason() -> None:
    error = unreachable("http://core:8080", "ConnectTimeout", may_have_been_applied=False)

    assert isinstance(error, CoreUnreachable)
    assert "http://core:8080" in str(error)
    assert "ConnectTimeout" in str(error)
    assert "Tell the user" in str(error)
    assert "may or may not" not in str(error)
    assert error.status is None


def test_unreachable_warns_when_a_write_may_have_been_applied() -> None:
    error = unreachable("http://core", "ReadTimeout", may_have_been_applied=True)

    assert "may or may not have been applied" in str(error)


def test_core_errors_are_tool_errors_so_the_agent_sees_their_message() -> None:
    from mcp.server.mcpserver.exceptions import ToolError

    assert isinstance(error_from_response(409, "x"), ToolError)
    assert isinstance(
        unreachable("http://core", "ConnectError", may_have_been_applied=False), ToolError
    )
