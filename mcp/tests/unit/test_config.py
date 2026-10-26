import os

import pytest
from pydantic import ValidationError

from automata_mcp.config import Settings


@pytest.fixture(autouse=True)
def clean_env(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in [name for name in os.environ if name.startswith("AUTOMATA_")]:
        monkeypatch.delenv(name)


def make(**overrides: object) -> Settings:
    return Settings(_env_file=None, **overrides)  # type: ignore[arg-type]


def test_defaults_match_the_plan() -> None:
    settings = make()

    assert settings.core_url == "http://localhost:8080"
    assert settings.transport == "stdio"
    assert (settings.http_host, settings.http_port) == ("0.0.0.0", 8000)
    assert settings.http_max_request_bytes == 4_194_304
    assert settings.request_timeout_seconds == 30
    assert settings.get_retries == 2
    assert settings.body_max_bytes == 65_536
    assert settings.body_max_fetch_bytes == 5_242_880
    assert settings.result_max_bytes == 100_000
    assert settings.wait_poll_seconds == 5
    assert (settings.wait_default_seconds, settings.wait_max_seconds) == (45, 55)
    assert settings.wait_max_job_ids == 20
    assert settings.wait_max_poll_errors == 3
    assert settings.target_enrich_limit == 50
    assert settings.log_level == "INFO"


def test_there_is_no_default_context_setting() -> None:
    assert "default_context_uuid" not in Settings.model_fields


def test_values_come_from_the_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AUTOMATA_CORE_URL", "http://core:8080")
    monkeypatch.setenv("AUTOMATA_TRANSPORT", "streamable-http")
    monkeypatch.setenv("AUTOMATA_HTTP_PORT", "9000")
    monkeypatch.setenv("AUTOMATA_GET_RETRIES", "0")
    monkeypatch.setenv("AUTOMATA_LOG_LEVEL", "DEBUG")

    settings = make()

    assert settings.core_url == "http://core:8080"
    assert settings.transport == "streamable-http"
    assert settings.http_port == 9000
    assert settings.get_retries == 0
    assert settings.log_level == "DEBUG"


def test_values_come_from_the_env_file(tmp_path: pytest.TempPathFactory) -> None:
    env_file = tmp_path / ".env"  # type: ignore[operator]
    env_file.write_text("AUTOMATA_CORE_URL=http://from-file:1\n")

    settings = Settings(_env_file=env_file)  # type: ignore[arg-type]

    assert settings.core_url == "http://from-file:1"


def test_trailing_slashes_of_the_core_url_are_removed() -> None:
    assert make(core_url="http://core:8080//").core_url == "http://core:8080"


@pytest.mark.parametrize("url", ["core:8080", "ftp://core", ""])
def test_core_url_must_be_http(url: str) -> None:
    with pytest.raises(ValidationError, match="http"):
        make(core_url=url)


def test_unknown_transport_is_rejected() -> None:
    with pytest.raises(ValidationError):
        make(transport="websocket")


@pytest.mark.parametrize("port", [0, 70000])
def test_port_must_be_valid(port: int) -> None:
    with pytest.raises(ValidationError):
        make(http_port=port)


def test_default_wait_must_not_exceed_the_maximum() -> None:
    with pytest.raises(ValidationError, match="wait_default_seconds"):
        make(wait_default_seconds=60, wait_max_seconds=55)


def test_body_cap_must_not_exceed_the_fetch_cap() -> None:
    with pytest.raises(ValidationError, match="body_max_bytes"):
        make(body_max_bytes=10, body_max_fetch_bytes=5)


@pytest.mark.parametrize("name", ["request_timeout_seconds", "body_max_bytes", "wait_max_job_ids"])
def test_sizes_and_durations_must_be_positive(name: str) -> None:
    with pytest.raises(ValidationError):
        make(**{name: 0})


def test_retries_may_be_zero_but_not_negative() -> None:
    assert make(get_retries=0).get_retries == 0
    with pytest.raises(ValidationError):
        make(get_retries=-1)
