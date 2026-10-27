from collections.abc import AsyncIterator, Callable
from typing import Any

import pytest
import respx

from automata_mcp.config import Settings
from helpers import Agent

BASE = "http://core.test:8080"
EMPTY_PAGE = {
    "content": [],
    "page": 0,
    "size": 1,
    "totalElements": 0,
    "totalPages": 0,
    "last": True,
}


@pytest.fixture
async def core_api() -> AsyncIterator[respx.MockRouter]:
    """A fake core; it already answers the check the server makes when it starts."""
    with respx.mock(base_url=BASE, assert_all_called=False) as router:
        router.get("/api/routines").respond(200, json=EMPTY_PAGE)
        yield router


@pytest.fixture
def agent(core_api: respx.MockRouter) -> Agent:
    """An MCP client connected in memory to the real server, whose core is the fake one."""
    return Agent(Settings(_env_file=None, core_url=BASE, get_retries=0))  # type: ignore[call-arg]


CTX = "6f1c0a52-0000-4000-8000-000000000001"
CP = f"/api/agent-contexts/uuid/{CTX}"


@pytest.fixture
def known_context(
    core_api: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> respx.MockRouter:
    core_api.get(CP).respond(200, json=load_fixture("agent_context_detailed"))
    core_api.get(f"{CP}/host").respond(200, json=load_fixture("host_detailed"))
    core_api.get(f"{CP}/resolved-requests").respond(200, json=load_fixture("resolved_targets"))
    core_api.get(f"{CP}/tenants").respond(200, json=load_fixture("context_tenants"))
    metadata = load_fixture("request_metadata")
    core_api.get(f"{CP}/requests/7/metadata").respond(200, json=metadata)
    core_api.get(f"{CP}/requests/21/metadata").respond(200, json=metadata | {"requestId": 21})
    core_api.get(f"{CP}/jobs").respond(200, json=load_fixture("job_summary_page"))
    core_api.get(f"{CP}/findings").respond(200, json=load_fixture("finding_summary_page"))
    core_api.get("/api/routines/1").respond(200, json=load_fixture("routine_detailed"))
    core_api.get("/api/routines/2").respond(404, text="Routine not found")
    return core_api
