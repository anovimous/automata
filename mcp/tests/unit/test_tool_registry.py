"""The tools of the server against the list of the plan (section 17.A)."""

import pytest
import respx
from mcp import Client

from automata_mcp.config import Settings
from automata_mcp.server import create_server

# The 37 tools of the plan.
ALL_TOOLS = frozenset(
    [
        "get_context_briefing",
        "get_context",
        "get_host",
        "list_targets",
        "list_tenants",
        "get_request",
        "get_request_raw",
        "get_request_body",
        "get_response",
        "get_response_raw",
        "get_response_body",
        "create_job",
        "list_jobs",
        "get_job",
        "wait_for_jobs",
        "get_job_result",
        "queue_job",
        "retry_job_preparation",
        "set_job_ai_result",
        "cancel_job",
        "list_routines",
        "get_routine",
        "list_vulnerabilities",
        "get_vulnerability",
        "list_wordlists",
        "get_wordlist",
        "create_wordlist",
        "create_finding",
        "get_finding",
        "list_findings",
        "update_finding",
        "export_body_property_names",
        "add_request",
        "list_request_jobs",
        "get_request_job",
        "get_request_job_result",
        "list_context_requests",
    ]
)

# The tools that exist so far: each phase that adds tools adds their names here.
IMPLEMENTED: frozenset[str] = frozenset(
    {
        # phase 3: context tools
        "get_context_briefing",
        "get_context",
        "get_host",
        "list_targets",
        "list_tenants",
        # phase 3: reference tools
        "list_routines",
        "get_routine",
        "list_vulnerabilities",
        "get_vulnerability",
        "list_wordlists",
        "get_wordlist",
        # phase 4: request and response tools
        "list_context_requests",
        "get_request",
        "get_request_raw",
        "get_request_body",
        "get_response",
        "get_response_raw",
        "get_response_body",
    }
)


def test_the_plan_has_37_tools() -> None:
    assert len(ALL_TOOLS) == 37


def test_implemented_tools_are_tools_of_the_plan() -> None:
    assert IMPLEMENTED <= ALL_TOOLS


async def test_the_server_registers_exactly_the_implemented_tools() -> None:
    settings = Settings(_env_file=None, core_url="http://core.test:8080", get_retries=0)  # type: ignore[call-arg]
    with respx.mock(base_url="http://core.test:8080", assert_all_called=False) as router:
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
        async with Client(create_server(settings)) as client:
            registered = {tool.name for tool in (await client.list_tools()).tools}

    assert registered == IMPLEMENTED


@pytest.mark.parametrize("name", sorted(ALL_TOOLS))
def test_tool_names_are_snake_case_verbs(name: str) -> None:
    assert name == name.lower()
    assert " " not in name
    assert name.split("_")[0] in {
        "get",
        "list",
        "create",
        "update",
        "queue",
        "retry",
        "set",
        "cancel",
        "wait",
        "export",
        "add",
    }
