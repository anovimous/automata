import asyncio
from collections.abc import AsyncIterator, Callable
from typing import Any
from uuid import UUID

import httpx
import pytest
import respx

from automata_mcp.core.client import CoreClient
from automata_mcp.core.models.context import ResolvedTarget
from automata_mcp.core.models.requests import RequestMetadata
from automata_mcp.services.targets import (
    ENRICH_CONCURRENCY,
    list_targets,
    request_summary,
    target_notes,
)

BASE = "http://core.test:8080"
CTX = UUID("6f1c0a52-0000-4000-8000-000000000001")
CP = f"/api/agent-contexts/uuid/{CTX}"


def target(
    request_id: int,
    *,
    eligible: bool = True,
    auto: bool = True,
    set_id: int | None = None,
) -> ResolvedTarget:
    return ResolvedTarget(
        request_id=request_id,
        automata_job_eligible=eligible,
        auto_approval=auto,
        source_item_type="EQUALITY_SET" if set_id else "REQUEST",  # type: ignore[arg-type]
        source_equality_set_id=set_id,
    )


@pytest.fixture
async def core() -> AsyncIterator[respx.MockRouter]:
    with respx.mock(base_url=BASE, assert_all_called=False) as router:
        yield router


@pytest.fixture
async def client() -> AsyncIterator[CoreClient]:
    async with CoreClient(BASE, get_retries=0) as instance:
        yield instance


def targets_json(*ids: int) -> list[dict[str, Any]]:
    return [
        {
            "requestId": request_id,
            "automataJobEligible": True,
            "autoApproval": True,
            "sourceItemType": "REQUEST",
        }
        for request_id in ids
    ]


# ---------------------------------------------------------------------------- request_summary


def test_request_summary_keeps_what_identifies_a_request(
    load_fixture: Callable[[str], Any],
) -> None:
    metadata = RequestMetadata.model_validate(load_fixture("request_metadata"))

    assert request_summary(metadata) == {
        "method": "POST",
        "computatedPath": "/api/users/{id}",
        "target": "/api/users/42?expand=items",
        "contentType": "application/json",
        "tenantId": 4,
        "responseId": 8,
        "bodySizeBytes": 183,
    }


def test_request_summary_leaves_out_what_the_request_does_not_have(
    load_fixture: Callable[[str], Any],
) -> None:
    data = load_fixture("request_metadata")
    data["tenantId"] = None
    data["responseId"] = None
    data["body"] = {"storage": "NONE", "contentType": None, "sizeBytes": 0}

    summary = request_summary(RequestMetadata.model_validate(data))

    assert set(summary) == {"method", "computatedPath", "target", "bodySizeBytes"}
    assert summary["bodySizeBytes"] == 0


# ---------------------------------------------------------------------------- target_notes


def test_no_targets_is_explained() -> None:
    assert "no targets" in target_notes([])[0]


def test_all_eligible_and_approved_targets_need_no_warning() -> None:
    assert target_notes([target(1), target(2)]) == []


def test_ineligible_targets_are_counted() -> None:
    notes = target_notes([target(1), target(2, eligible=False), target(3, eligible=False)])

    assert notes == [
        "2 of 3 targets are not job-eligible (their body is not empty, JSON or form): "
        "read them, but test them by other means."
    ]


def test_targets_that_need_approval_are_counted() -> None:
    notes = target_notes([target(1, auto=False), target(2)])

    assert len(notes) == 1
    assert "A job on 1 of 2 targets needs a human to approve it" in notes[0]


def test_equality_sets_are_counted_once_each() -> None:
    notes = target_notes([target(1, set_id=5), target(2, set_id=5), target(3, set_id=6)])

    assert notes == [
        "2 equality set(s): requests of one set are equivalent, so test one representative per set."
    ]


# ---------------------------------------------------------------------------- list_targets


async def test_targets_are_enriched_with_their_request(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core.get(f"{CP}/resolved-requests").respond(200, json=targets_json(7, 21))
    metadata = load_fixture("request_metadata")
    core.get(f"{CP}/requests/7/metadata").respond(200, json=metadata)
    core.get(f"{CP}/requests/21/metadata").respond(
        200, json=metadata | {"requestId": 21, "tenantId": None}
    )

    result = await list_targets(client, CTX, with_summary=True, enrich_limit=50)

    assert result["total"] == 2
    assert result["enriched"] == 2
    assert [item["requestId"] for item in result["targets"]] == [7, 21]
    assert result["targets"][0]["computatedPath"] == "/api/users/{id}"
    assert result["targets"][0]["tenantId"] == 4
    assert "tenantId" not in result["targets"][1]
    assert result["notes"] == []


async def test_without_summary_no_request_is_read(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.get(f"{CP}/resolved-requests").respond(200, json=targets_json(7, 21))
    metadata = core.get(url__regex=rf"{CP}/requests/\d+/metadata")

    result = await list_targets(client, CTX, with_summary=False, enrich_limit=50)

    assert result["total"] == 2
    assert "enriched" not in result
    assert metadata.call_count == 0
    assert "method" not in result["targets"][0]


async def test_only_the_first_targets_up_to_the_limit_are_enriched(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core.get(f"{CP}/resolved-requests").respond(200, json=targets_json(1, 2, 3))
    metadata = core.get(url__regex=rf"{CP}/requests/\d+/metadata").respond(
        200, json=load_fixture("request_metadata")
    )

    result = await list_targets(client, CTX, with_summary=True, enrich_limit=2)

    assert metadata.call_count == 2
    assert result["enriched"] == 2
    assert "method" in result["targets"][1]
    assert "method" not in result["targets"][2]
    assert "Only 2 of 3 targets carry their request summary" in result["notes"][-1]


async def test_a_request_that_cannot_be_read_is_left_plain(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core.get(f"{CP}/resolved-requests").respond(200, json=targets_json(7, 21))
    core.get(f"{CP}/requests/7/metadata").respond(200, json=load_fixture("request_metadata"))
    core.get(f"{CP}/requests/21/metadata").respond(404, text="gone")

    result = await list_targets(client, CTX, with_summary=True, enrich_limit=50)

    assert result["enriched"] == 1
    assert "method" in result["targets"][0]
    assert result["targets"][1] == {
        "requestId": 21,
        "automataJobEligible": True,
        "autoApproval": True,
        "sourceItemType": "REQUEST",
    }
    assert "Only 1 of 2" in result["notes"][-1]


async def test_enrichment_runs_in_parallel_but_is_bounded(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    core.get(f"{CP}/resolved-requests").respond(200, json=targets_json(*range(1, 31)))
    running = 0
    peak = 0
    metadata = load_fixture("request_metadata")

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal running, peak
        running += 1
        peak = max(peak, running)
        await asyncio.sleep(0.01)
        running -= 1
        return httpx.Response(200, json=metadata)

    core.get(url__regex=rf"{CP}/requests/\d+/metadata").mock(side_effect=handler)

    result = await list_targets(client, CTX, with_summary=True, enrich_limit=50)

    assert result["enriched"] == 30
    assert 1 < peak <= ENRICH_CONCURRENCY


async def test_a_context_without_targets_says_so(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.get(f"{CP}/resolved-requests").respond(200, json=[])

    result = await list_targets(client, CTX, with_summary=True, enrich_limit=50)

    assert result["targets"] == []
    assert result["total"] == 0
    assert "enriched" not in result
    assert "no targets" in result["notes"][0]


async def test_a_failure_to_list_the_targets_is_raised(
    client: CoreClient, core: respx.MockRouter
) -> None:
    from automata_mcp.core.errors import CoreNotFound

    core.get(f"{CP}/resolved-requests").respond(404, text="Agent context not found")

    with pytest.raises(CoreNotFound):
        await list_targets(client, CTX, with_summary=True, enrich_limit=50)
