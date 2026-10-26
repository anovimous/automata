from collections.abc import AsyncIterator, Callable
from typing import Any
from uuid import UUID

import pytest
import respx

from automata_mcp.config import Settings
from automata_mcp.core.client import CoreClient
from automata_mcp.core.errors import CoreNotFound, CoreServerError
from automata_mcp.core.models.context import AgentContextDetailed, HostDetailed
from automata_mcp.core.models.enums import RepresentativeStrategy
from automata_mcp.core.models.reference import RoutineDetailed
from automata_mcp.core.models.tenants import AgentContextTenant
from automata_mcp.services.briefing import (
    briefing_notes,
    build_briefing,
    context_section,
    fetch_routine,
    routine_section,
    strategy_note,
    unwrap,
)

BASE = "http://core.test:8080"
CTX = UUID("6f1c0a52-0000-4000-8000-000000000001")
CP = f"/api/agent-contexts/uuid/{CTX}"


@pytest.fixture
async def core() -> AsyncIterator[respx.MockRouter]:
    with respx.mock(base_url=BASE, assert_all_called=False) as router:
        yield router


@pytest.fixture
async def client() -> AsyncIterator[CoreClient]:
    async with CoreClient(BASE, get_retries=0) as instance:
        yield instance


@pytest.fixture
def settings() -> Settings:
    return Settings(_env_file=None, core_url=BASE, target_enrich_limit=50)  # type: ignore[call-arg]


@pytest.fixture
def mock_core(core: respx.MockRouter, load_fixture: Callable[[str], Any]) -> respx.MockRouter:
    """A core that knows the sample context, host, targets, tenants and routine 1 (not 2)."""
    core.get(CP).respond(200, json=load_fixture("agent_context_detailed"))
    core.get(f"{CP}/host").respond(200, json=load_fixture("host_detailed"))
    core.get(f"{CP}/resolved-requests").respond(200, json=load_fixture("resolved_targets"))
    core.get(f"{CP}/tenants").respond(200, json=load_fixture("context_tenants"))
    metadata = load_fixture("request_metadata")
    core.get(f"{CP}/requests/7/metadata").respond(200, json=metadata)
    core.get(f"{CP}/requests/21/metadata").respond(200, json=metadata | {"requestId": 21})
    core.get(f"{CP}/jobs").respond(200, json=load_fixture("job_summary_page"))
    core.get(f"{CP}/findings").respond(200, json=load_fixture("finding_summary_page"))
    core.get("/api/routines/1").respond(200, json=load_fixture("routine_detailed"))
    core.get("/api/routines/2").respond(404, text="Routine not found")
    return core


def sample(
    load_fixture: Callable[[str], Any],
) -> tuple[AgentContextDetailed, HostDetailed, list[AgentContextTenant]]:
    context = AgentContextDetailed.model_validate(load_fixture("agent_context_detailed"))
    host = HostDetailed.model_validate(load_fixture("host_detailed"))
    tenants = [AgentContextTenant.model_validate(item) for item in load_fixture("context_tenants")]
    return context, host, tenants


# ---------------------------------------------------------------------------- pure helpers


def test_unwrap_returns_a_value_and_raises_an_exception() -> None:
    assert unwrap(5) == 5
    with pytest.raises(ValueError, match="boom"):
        unwrap(ValueError("boom"))


def test_context_section_keeps_the_fields_of_the_plan(load_fixture: Callable[[str], Any]) -> None:
    context, _, _ = sample(load_fixture)

    assert context_section(context) == {
        "uuid": "6f1c0a52-0000-4000-8000-000000000001",
        "name": "Users API",
        "description": "Account endpoints of api.example.com",
        "representativeStrategy": "LATEST",
        "defaultAutoApproval": False,
        "defaults": {"duration": "SHORT", "verbosity": "SUMMARIZED", "priority": 2},
        "maxRate": 30,
    }


def test_context_section_leaves_out_what_is_not_set(load_fixture: Callable[[str], Any]) -> None:
    data = load_fixture("agent_context_detailed") | {
        "description": None,
        "defaultDuration": None,
        "defaultVerbosity": None,
        "defaultPriority": None,
        "maxRate": None,
    }

    section = context_section(AgentContextDetailed.model_validate(data))

    assert "description" not in section
    assert "maxRate" not in section
    assert section["defaults"] == {}


def test_routine_section_keeps_what_the_agent_needs(load_fixture: Callable[[str], Any]) -> None:
    routine = RoutineDetailed.model_validate(load_fixture("routine_detailed"))

    section = routine_section(routine)

    assert set(section) == {
        "id",
        "key",
        "description",
        "resultContentType",
        "configSchema",
        "wordlistRequired",
        "isAvailableAtConsumer",
        "allowedTargets",
    }
    assert section["configSchema"]["type"] == "object"
    assert section["allowedTargets"] == ["SINGLE_REQUEST"]


async def test_a_routine_is_fetched(client: CoreClient, mock_core: respx.MockRouter) -> None:
    section = await fetch_routine(client, 1)

    assert section["key"] == "fuzz"


async def test_a_routine_that_cannot_be_read_gives_an_error_slot(
    client: CoreClient, mock_core: respx.MockRouter
) -> None:
    section = await fetch_routine(client, 2)

    assert section["id"] == 2
    assert "Routine not found" in section["error"]


def test_strategy_notes() -> None:
    assert "LATEST" in (strategy_note(RepresentativeStrategy.LATEST) or "")
    assert "older" in (strategy_note(RepresentativeStrategy.LATEST) or "")
    assert "AI_JUDGEMENT" in (strategy_note(RepresentativeStrategy.AI_JUDGEMENT) or "")
    assert strategy_note(RepresentativeStrategy("SOMETHING_NEW")) is None


# ---------------------------------------------------------------------------- briefing_notes


def test_notes_of_the_sample_context(load_fixture: Callable[[str], Any]) -> None:
    context, host, tenants = sample(load_fixture)
    routines = [{"id": 1, "key": "fuzz", "wordlistRequired": True, "isAvailableAtConsumer": True}]

    notes = briefing_notes(context, host, tenants, routines)

    assert any(note.startswith("Strategy LATEST") for note in notes)
    assert "A job's rate is capped by the host rate limit 50, context maxRate 30." in notes
    assert any("sensitive" in note for note in notes)
    assert any("tenant-b" in note and "access-control" in note for note in notes)
    assert any("fuzz" in note and "wordlist" in note for note in notes)
    assert not any("out of scope" in note for note in notes)


def test_a_host_rate_limit_of_zero_is_called_out(load_fixture: Callable[[str], Any]) -> None:
    context, host, tenants = sample(load_fixture)
    host.host_rate_limit = 0

    notes = briefing_notes(context, host, tenants, [])

    assert any("rate limit of 0" in note and "Tell the user" in note for note in notes)
    assert not any("capped by" in note for note in notes)


def test_the_rate_note_works_without_a_context_max_rate(
    load_fixture: Callable[[str], Any],
) -> None:
    context, host, tenants = sample(load_fixture)
    context.max_rate = None

    notes = briefing_notes(context, host, tenants, [])

    assert "A job's rate is capped by the host rate limit 50." in notes


def test_a_host_out_of_scope_is_called_out(load_fixture: Callable[[str], Any]) -> None:
    context, host, tenants = sample(load_fixture)
    host.out_of_scope = True

    assert "The core marks this host as out of scope." in briefing_notes(context, host, tenants, [])


def test_without_tenants_there_are_no_tenant_notes(load_fixture: Callable[[str], Any]) -> None:
    context, host, _ = sample(load_fixture)

    notes = briefing_notes(context, host, [], [])

    assert not any("Tenant" in note for note in notes)


def test_routines_the_worker_does_not_offer_and_unreadable_ones_are_listed(
    load_fixture: Callable[[str], Any],
) -> None:
    context, host, tenants = sample(load_fixture)
    routines = [
        {"id": 1, "key": "fuzz", "isAvailableAtConsumer": False},
        {"id": 2, "error": "not found"},
    ]

    notes = briefing_notes(context, host, tenants, routines)

    assert any("Not available at the worker" in note and "fuzz" in note for note in notes)
    assert any("could not be read: [2]" in note for note in notes)


# ---------------------------------------------------------------------------- build_briefing


async def test_the_briefing_gathers_everything(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    briefing = await build_briefing(client, settings, CTX)

    assert set(briefing) == {
        "context",
        "host",
        "targets",
        "tenants",
        "allowedRoutines",
        "recentJobs",
        "recentFindings",
        "notes",
    }
    assert briefing["context"]["name"] == "Users API"
    assert briefing["host"]["host"] == "api.example.com"
    assert [target["requestId"] for target in briefing["targets"]] == [7, 21]
    assert briefing["targets"][0]["method"] == "POST"
    assert [tenant["tenantId"] for tenant in briefing["tenants"]] == [4, 5]
    assert briefing["tenants"][0]["authData"]["data"]["headers"][0]["key"] == "Authorization"


async def test_allowed_routines_are_read_and_an_unreadable_one_is_reported_in_its_slot(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    briefing = await build_briefing(client, settings, CTX)

    first, second = briefing["allowedRoutines"]
    assert first["key"] == "fuzz"
    assert first["configSchema"]["type"] == "object"
    assert second["id"] == 2
    assert "Routine not found" in second["error"]


async def test_the_notes_come_from_the_targets_and_the_situation(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    notes = (await build_briefing(client, settings, CTX))["notes"]

    assert any("1 of 2 targets are not job-eligible" in note for note in notes)
    assert any("A job on 1 of 2 targets needs a human to approve it" in note for note in notes)
    assert any(note.startswith("Strategy LATEST") for note in notes)
    assert any("Routines meant to run with a wordlist: fuzz" in note for note in notes)
    assert any("could not be read: [2]" in note for note in notes)


async def test_the_enrich_limit_of_the_settings_is_used(
    client: CoreClient, mock_core: respx.MockRouter
) -> None:
    limited = Settings(_env_file=None, core_url=BASE, target_enrich_limit=1)  # type: ignore[call-arg]

    briefing = await build_briefing(client, limited, CTX)

    assert "method" in briefing["targets"][0]
    assert "method" not in briefing["targets"][1]


async def test_a_context_that_does_not_exist_fails_the_briefing_with_its_error(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    mock_core.get(CP).respond(404, text="Agent context not found")

    with pytest.raises(CoreNotFound, match="Agent context not found"):
        await build_briefing(client, settings, CTX)


@pytest.mark.parametrize("part", ["/host", "/resolved-requests", "/tenants"])
async def test_a_required_part_that_fails_fails_the_briefing(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings, part: str
) -> None:
    mock_core.get(f"{CP}{part}").respond(500, text="database down")

    with pytest.raises(CoreServerError, match="database down"):
        await build_briefing(client, settings, CTX)


async def test_the_error_of_the_context_comes_before_the_others(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    mock_core.get(CP).respond(404, text="Agent context not found")
    mock_core.get(f"{CP}/host").respond(500, text="database down")

    with pytest.raises(CoreNotFound):
        await build_briefing(client, settings, CTX)


# ---------------------------------------------------------------------------- recent jobs


async def test_the_recent_jobs_are_the_ten_newest_of_the_context(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    briefing = await build_briefing(client, settings, CTX)

    jobs_route = next(r for r in mock_core.routes if r.name is None and "jobs" in str(r.pattern))
    assert dict(jobs_route.calls.last.request.url.params) == {"page": "0", "size": "10"}
    assert [job["id"] for job in briefing["recentJobs"]] == [31, 30]
    assert briefing["recentJobs"][0]["waitingForAction"] is True
    assert briefing["recentJobs"][1]["terminal"] is True


async def test_jobs_that_wait_for_someone_are_called_out(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    notes = (await build_briefing(client, settings, CTX))["notes"]

    assert any(note.startswith("Jobs waiting for someone: [31].") for note in notes)


async def test_no_note_when_no_job_waits(
    client: CoreClient,
    mock_core: respx.MockRouter,
    settings: Settings,
    load_fixture: Callable[[str], Any],
) -> None:
    page = load_fixture("job_summary_page")
    page["content"] = [page["content"][1]]
    mock_core.get(f"{CP}/jobs").respond(200, json=page)

    notes = (await build_briefing(client, settings, CTX))["notes"]

    assert not any("waiting for someone" in note for note in notes)


async def test_a_context_without_jobs_gives_an_empty_list(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    mock_core.get(f"{CP}/jobs").respond(
        200,
        json={
            "content": [],
            "page": 0,
            "size": 10,
            "totalElements": 0,
            "totalPages": 0,
            "last": True,
        },
    )

    assert (await build_briefing(client, settings, CTX))["recentJobs"] == []


@pytest.mark.parametrize("status", [404, 500])
async def test_jobs_that_cannot_be_read_do_not_fail_the_briefing(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings, status: int
) -> None:
    mock_core.get(f"{CP}/jobs").respond(status, text="jobs are broken")

    briefing = await build_briefing(client, settings, CTX)

    assert "jobs are broken" in briefing["recentJobs"]["error"]
    assert briefing["context"]["name"] == "Users API"
    assert briefing["targets"]


async def test_an_unexpected_failure_reading_the_jobs_is_not_hidden(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    mock_core.get(f"{CP}/jobs").mock(side_effect=RuntimeError("bug"))

    with pytest.raises(RuntimeError, match="bug"):
        await build_briefing(client, settings, CTX)


# ---------------------------------------------------------------------------- recent findings


async def test_the_recent_findings_are_the_five_newest_with_their_total(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    briefing = await build_briefing(client, settings, CTX)

    route = next(r for r in mock_core.routes if "findings" in str(r.pattern))
    assert dict(route.calls.last.request.url.params) == {"page": "0", "size": "5"}
    assert briefing["recentFindings"]["total"] == 2
    items = briefing["recentFindings"]["items"]
    assert [f["id"] for f in items] == [5, 4]
    assert items[0]["humanVerdict"] == "TRUE_POSITIVE"
    assert items[0]["origin"] == "THIS_CONTEXT"
    assert items[1]["origin"] == "USER"


async def test_human_verdicts_on_the_latest_findings_are_called_out(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    notes = (await build_briefing(client, settings, CTX))["notes"]

    assert any(
        "1 of the 2 latest findings have a human verdict (1 true positive, 0 false positive)" in n
        for n in notes
    )


async def test_both_kinds_of_verdict_are_counted(
    client: CoreClient,
    mock_core: respx.MockRouter,
    settings: Settings,
    load_fixture: Callable[[str], Any],
) -> None:
    page = load_fixture("finding_summary_page")
    page["content"][1]["humanVerdict"] = "FALSE_POSITIVE"
    mock_core.get(f"{CP}/findings").respond(200, json=page)

    notes = (await build_briefing(client, settings, CTX))["notes"]

    assert any("2 of the 2 latest findings" in n and "1 false positive" in n for n in notes)


async def test_no_verdict_note_without_verdicts(
    client: CoreClient,
    mock_core: respx.MockRouter,
    settings: Settings,
    load_fixture: Callable[[str], Any],
) -> None:
    page = load_fixture("finding_summary_page")
    for finding in page["content"]:
        finding["humanVerdict"] = None
    mock_core.get(f"{CP}/findings").respond(200, json=page)

    notes = (await build_briefing(client, settings, CTX))["notes"]

    assert not any("human verdict" in n for n in notes)


async def test_a_context_without_findings_gives_a_zero_total(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    empty = {"content": [], "page": 0, "size": 5, "totalElements": 0, "totalPages": 0, "last": True}
    mock_core.get(f"{CP}/findings").respond(200, json=empty)

    assert (await build_briefing(client, settings, CTX))["recentFindings"] == {
        "total": 0,
        "items": [],
    }


@pytest.mark.parametrize("status", [404, 500])
async def test_findings_that_cannot_be_read_do_not_fail_the_briefing(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings, status: int
) -> None:
    mock_core.get(f"{CP}/findings").respond(status, text="findings are broken")

    briefing = await build_briefing(client, settings, CTX)

    assert "findings are broken" in briefing["recentFindings"]["error"]
    assert briefing["recentJobs"]
    assert briefing["targets"]


async def test_an_unexpected_failure_reading_the_findings_is_not_hidden(
    client: CoreClient, mock_core: respx.MockRouter, settings: Settings
) -> None:
    mock_core.get(f"{CP}/findings").mock(side_effect=RuntimeError("bug"))

    with pytest.raises(RuntimeError, match="bug"):
        await build_briefing(client, settings, CTX)
