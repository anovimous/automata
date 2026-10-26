from collections.abc import AsyncIterator

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
