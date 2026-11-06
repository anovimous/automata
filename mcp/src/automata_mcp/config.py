"""Settings of the server: environment variables (prefix ``AUTOMATA_``) and an optional ``.env``."""

from typing import Literal, Self

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="AUTOMATA_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    core_url: str = "http://localhost:8080"
    transport: Literal["stdio", "streamable-http"] = "stdio"
    http_host: str = "0.0.0.0"  # the Docker default, see the plan (section 15)
    http_port: int = Field(default=8000, ge=1, le=65535)
    http_max_request_bytes: int = Field(default=4_194_304, gt=0)

    request_timeout_seconds: float = Field(default=30, gt=0)
    get_retries: int = Field(default=2, ge=0)

    body_max_bytes: int = Field(default=65_536, gt=0)
    body_max_fetch_bytes: int = Field(default=5_242_880, gt=0)
    result_max_bytes: int = Field(default=100_000, gt=0)

    wait_poll_seconds: float = Field(default=5, gt=0)
    wait_default_seconds: float = Field(default=45, gt=0)
    wait_max_seconds: float = Field(default=55, gt=0)
    wait_max_job_ids: int = Field(default=20, gt=0)
    wait_max_poll_errors: int = Field(default=3, gt=0)

    target_enrich_limit: int = Field(default=50, ge=0)
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"] = "INFO"

    @model_validator(mode="after")
    def _normalize_and_check(self) -> Self:
        self.core_url = self.core_url.rstrip("/")
        if not self.core_url.startswith(("http://", "https://")):
            raise ValueError("core_url must start with http:// or https://")
        if self.wait_default_seconds > self.wait_max_seconds:
            raise ValueError("wait_default_seconds must not exceed wait_max_seconds")
        if self.body_max_bytes > self.body_max_fetch_bytes:
            raise ValueError("body_max_bytes must not exceed body_max_fetch_bytes")
        return self
