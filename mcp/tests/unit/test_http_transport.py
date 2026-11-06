"""The streamable HTTP app of the server (what Docker runs), through Starlette's test client.

The compose service reaches the server as ``automata-mcp:8000``; these tests pin what the SDK does
with that Host header and with oversized bodies.
"""

import pytest
import respx
from starlette.testclient import TestClient

from automata_mcp.config import Settings
from automata_mcp.server import create_server, http_run_options

BASE = "http://core.test:8080"
INITIALIZE = {
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
        "protocolVersion": "2025-06-18",
        "capabilities": {},
        "clientInfo": {"name": "test", "version": "1"},
    },
}
HEADERS = {"Accept": "application/json, text/event-stream", "Content-Type": "application/json"}


@pytest.fixture(autouse=True)
def core() -> respx.MockRouter:
    with respx.mock(base_url=BASE, assert_all_called=False) as router:
        router.get("/api/routines").respond(
            200,
            json={
                "content": [],
                "page": 0,
                "size": 1,
                "totalElements": 0,
                "totalPages": 0,
                "last": True,
            },
        )
        yield router


def post(
    host_header: str, *, bind_host: str, max_bytes: int = 4_194_304, content: bytes | None = None
) -> int:
    """POST to /mcp of a fresh app (a session manager can run only once) and return the status."""
    settings = Settings(_env_file=None, core_url=BASE, get_retries=0)  # type: ignore[call-arg]
    app = create_server(settings).streamable_http_app(
        host=bind_host, max_request_body_size=max_bytes
    )
    with TestClient(app, base_url=f"http://{host_header}") as http:
        if content is None:
            return http.post("/mcp", headers=HEADERS, json=INITIALIZE).status_code
        return http.post("/mcp", headers=HEADERS, content=content).status_code


def test_bound_to_all_interfaces_the_compose_service_name_is_accepted() -> None:
    assert post("automata-mcp:8000", bind_host="0.0.0.0") == 200


def test_bound_to_localhost_a_foreign_host_header_is_refused() -> None:
    assert post("automata-mcp:8000", bind_host="127.0.0.1") == 421
    assert post("localhost:8000", bind_host="127.0.0.1") == 200


def test_the_configured_default_host_is_the_one_that_accepts_the_compose_name() -> None:
    settings = Settings(_env_file=None)  # type: ignore[call-arg]

    assert http_run_options(settings)["host"] == "0.0.0.0"


def test_a_body_over_the_configured_limit_is_refused() -> None:
    body = b'{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"x":"' + b"a" * 1000 + b'"}}'

    assert post("automata-mcp:8000", bind_host="0.0.0.0", max_bytes=300, content=body) == 413
