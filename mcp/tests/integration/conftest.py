"""Integration tests: the real server against a real Automata core.

They run only with ``AUTOMATA_IT=1`` (the core is read from ``AUTOMATA_CORE_URL``, default
``http://localhost:8080``). The core needs its database, RabbitMQ and object storage, so the
simplest way is its own ``docker compose up``. Each run seeds a context of its own with unique
names, so it can be repeated against the same core. Jobs are created, approved and queued but not
run: running them is the worker's job and no worker is needed here.
"""

import base64
import os
import uuid
from collections.abc import Iterator
from dataclasses import dataclass
from typing import Any

import httpx
import pytest

from automata_mcp.config import Settings
from helpers import Agent

# Read once at import: the autouse fixture of tests/conftest.py removes AUTOMATA_* per test.
ENABLED = os.environ.get("AUTOMATA_IT") == "1"
CORE_URL = os.environ.get("AUTOMATA_CORE_URL", "http://localhost:8080").rstrip("/")


def pytest_collection_modifyitems(config: pytest.Config, items: list[pytest.Item]) -> None:
    if ENABLED:
        return
    skip = pytest.mark.skip(reason="integration tests need AUTOMATA_IT=1 and a running core")
    for item in items:
        if "tests/integration" in str(item.path).replace("\\", "/"):
            item.add_marker(skip)
            item.add_marker(pytest.mark.integration)


def b64(text: str) -> str:
    return base64.b64encode(text.encode()).decode()


@dataclass(frozen=True)
class Seed:
    """What the seed created: ids in the core, and the uuid of the context."""

    suffix: str
    context_uuid: str
    context_id: int
    host_name: str
    host_id: int
    tenant_id: int
    routine_id: int
    vulnerability_id: int
    parent_vulnerability_id: int
    request_id: int
    other_request_id: int


def created_id(response: httpx.Response) -> int:
    assert response.status_code in (200, 201), (
        f"{response.request.url}: {response.status_code} {response.text}"
    )
    location = response.headers.get("Location")
    if location:
        return int(location.rstrip("/").rsplit("/", 1)[-1])
    return int(response.json()["id"])


@pytest.fixture(scope="session")
def seed() -> Iterator[Seed]:
    """A program, host, tenant, routine, vulnerability and two requests, in one new context."""
    suffix = uuid.uuid4().hex[:8]
    host_name = f"it-{suffix}.example.com"
    with httpx.Client(base_url=CORE_URL, timeout=30) as core:
        program = created_id(
            core.post(
                "/api/programs", json={"name": f"it-program-{suffix}", "programRateLimit": 10}
            )
        )
        host = created_id(
            core.post(
                "/api/hosts",
                json={
                    "host": host_name,
                    "scope": "FQDN",
                    "hostRateLimit": 50,
                    "shortRateLimit": 20,
                    "longRateLimit": 10,
                    "programId": program,
                },
            )
        )
        tenant = created_id(
            core.post(
                "/api/tenants",
                json={
                    "name": f"it-tenant-{suffix}",
                    "email": f"it-{suffix}@example.test",
                    "hostId": host,
                },
            )
        )
        # The child has a parent on purpose: the core fails to show a vulnerability without one
        # (see test_a_root_vulnerability_can_be_read, an expected failure).
        parent = created_id(
            core.post("/api/vulnerabilities", json={"name": f"IT-parent-{uuid.uuid4().hex[:8]}"})
        )
        vulnerability = created_id(
            core.post(
                "/api/vulnerabilities", json={"name": f"IT BOLA {suffix}", "parentId": parent}
            )
        )
        routine = created_id(
            core.post(
                "/api/routines",
                json={
                    "key": f"it-fuzz-{suffix}",
                    "description": "Integration test routine",
                    "isAvailableAtConsumer": True,
                    "wordlistRequired": True,
                    "overhead": "HIGH",
                    "protocol": "HTTP",
                    "allowedTargets": ["SINGLE_REQUEST"],
                    "vulnerabilityId": vulnerability,
                    "configSchema": {
                        "type": "object",
                        "properties": {
                            "rawRequestBase64": {"type": "string"},
                            "flags": {"type": "array"},
                        },
                    },
                    "resultContentType": "JSON",
                },
            )
        )
        request = (
            f"POST /api/users/42 HTTP/1.1\r\nHost: {host_name}\r\n"
            "Content-Type: application/json\r\n"
            'Content-Length: 31\r\n\r\n{"email":"a@b.test","role":"x"}'
        )
        response = (
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 17\r\n\r\n"
            '{"id":42,"ok":1}'
        )
        other = f"GET /api/users/7?expand=orders HTTP/1.1\r\nHost: {host_name}\r\n\r\n"
        first = core.post(
            "/api/requests/raw",
            json={
                "requestBase64": b64(request),
                "source": "MANUAL",
                "hostId": host,
                "tenantId": tenant,
                "responseBase64": b64(response),
            },
        )
        second = core.post(
            "/api/requests/raw",
            json={"requestBase64": b64(other), "source": "MANUAL", "hostId": host},
        )
        assert first.status_code == 201, first.text
        assert second.status_code == 201, second.text
        request_id = int(first.json()["requestId"])
        other_id = int(second.json()["requestId"])
        context = created_id(
            core.post(
                "/api/agent-contexts",
                json={
                    "name": f"it-context-{suffix}",
                    "description": "Integration test context",
                    "hostId": host,
                    "defaultAutoApproval": False,
                    "representativeStrategy": "LATEST",
                    "allowedRoutineIds": [routine],
                    "defaultDuration": "SHORT",
                    "defaultVerbosity": "SUMMARIZED",
                    "defaultPriority": 2,
                    "maxRate": 30,
                    "tenantIds": [tenant],
                },
            )
        )
        for added in (request_id, other_id):
            item = core.post(
                f"/api/agent-contexts/{context}/items",
                json={"itemType": "REQUEST", "requestId": added},
            )
            assert item.status_code in (200, 201), item.text
        detail: dict[str, Any] = core.get(f"/api/agent-contexts/{context}").json()
    yield Seed(
        suffix=suffix,
        context_uuid=detail["uuid"],
        context_id=context,
        host_name=host_name,
        host_id=host,
        tenant_id=tenant,
        routine_id=routine,
        vulnerability_id=vulnerability,
        parent_vulnerability_id=parent,
        request_id=request_id,
        other_request_id=other_id,
    )


@pytest.fixture
def real_agent() -> Agent:
    """An MCP client of the real server pointed at the real core."""
    return Agent(Settings(_env_file=None, core_url=CORE_URL, get_retries=0))  # type: ignore[call-arg]
