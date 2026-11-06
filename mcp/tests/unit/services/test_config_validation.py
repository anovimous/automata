import base64

from automata_mcp.services.config_validation import expand_raw_request_text


def decoded(config: dict[str, object]) -> bytes:
    return base64.b64decode(str(config["rawRequestBase64"]))


def test_nothing_to_do_without_a_config_or_without_the_text() -> None:
    assert expand_raw_request_text(None) is None
    assert expand_raw_request_text({}) == {}
    assert expand_raw_request_text({"flags": []}) == {"flags": []}


def test_the_text_becomes_base64_of_the_normalized_message() -> None:
    config = {"rawRequestText": "GET /api/users/FUZZ HTTP/1.1\nHost: api.example.com\n\n"}

    result = expand_raw_request_text(config)

    assert result is not None
    assert "rawRequestText" not in result
    assert decoded(result) == b"GET /api/users/FUZZ HTTP/1.1\r\nHost: api.example.com\r\n\r\n"


def test_the_other_settings_are_kept() -> None:
    config = {"rawRequestText": "GET / HTTP/1.1\n\n", "flags": [{"key": "-mc", "value": "200"}]}

    result = expand_raw_request_text(config)

    assert result is not None
    assert result["flags"] == [{"key": "-mc", "value": "200"}]


def test_the_input_is_not_modified() -> None:
    config = {"rawRequestText": "GET / HTTP/1.1\n\n"}

    expand_raw_request_text(config)

    assert config == {"rawRequestText": "GET / HTTP/1.1\n\n"}


def test_an_existing_base64_wins_and_the_text_is_dropped() -> None:
    config = {"rawRequestText": "GET /text HTTP/1.1\n\n", "rawRequestBase64": "QUJD"}

    assert expand_raw_request_text(config) == {"rawRequestBase64": "QUJD"}


def test_a_text_that_is_not_a_string_is_left_for_the_core_to_reject() -> None:
    config = {"rawRequestText": 5}

    assert expand_raw_request_text(config) == {"rawRequestText": 5}


def test_a_body_in_the_text_is_kept_byte_for_byte() -> None:
    config = {"rawRequestText": 'POST /x HTTP/1.1\nHost: x\n\n{"a":\n  "FUZZ"}'}

    result = expand_raw_request_text(config)

    assert result is not None
    assert decoded(result).endswith(b'\r\n\r\n{"a":\n  "FUZZ"}')
