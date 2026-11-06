import json
from collections.abc import AsyncIterator, Callable
from typing import Any
from uuid import UUID

import pytest
import respx
from mcp.server.mcpserver.exceptions import ToolError

from automata_mcp.core.client import CoreClient
from automata_mcp.services import findings as finding_service

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


async def create(client: CoreClient, **overrides: Any) -> dict[str, Any]:
    arguments: dict[str, Any] = {
        "request_id": 7,
        "vulnerability_id": 12,
        "severity": 7.5,
        "confidence": 8.0,
        "status": "CONFIRMED",
        "evidence": "GET /api/users/2 returned tenant B's profile",
        "poc_reproduction_steps": "1. Log in as A. 2. GET /api/users/2.",
        "job_id": 31,
        "ai_description": "BOLA",
    }
    return await finding_service.create_finding(client, CTX, **(arguments | overrides))


async def test_create_posts_every_field_and_returns_the_id_with_the_review_note(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    post = core.post(f"{CP}/findings").respond(201, headers={"Location": f"{CP}/findings/5"})

    result = await create(client)

    assert json.loads(post.calls.last.request.content) == {
        "requestId": 7,
        "jobId": 31,
        "vulnerabilityId": 12,
        "severity": 7.5,
        "status": "CONFIRMED",
        "confidence": 8.0,
        "evidence": "GET /api/users/2 returned tenant B's profile",
        "pocReproductionSteps": "1. Log in as A. 2. GET /api/users/2.",
        "aiDescription": "BOLA",
    }
    assert result == {"findingId": 5, "next_step": finding_service.REVIEW_NOTE}
    assert "TRUE_POSITIVE or FALSE_POSITIVE" in result["next_step"]


async def test_create_leaves_out_what_was_not_given(
    client: CoreClient, core: respx.MockRouter
) -> None:
    post = core.post(f"{CP}/findings").respond(201, headers={"Location": f"{CP}/findings/6"})

    await create(
        client, status="SUGGESTED", poc_reproduction_steps=None, job_id=None, ai_description=None
    )

    sent = json.loads(post.calls.last.request.content)
    assert sent["status"] == "SUGGESTED"
    assert set(sent) == {
        "requestId",
        "vulnerabilityId",
        "severity",
        "status",
        "confidence",
        "evidence",
    }


async def test_create_does_not_check_what_the_core_checks(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post(f"{CP}/findings").respond(400, text="pocReproductionSteps is required when CONFIRMED")

    from automata_mcp.core.errors import CoreBadRequest

    with pytest.raises(CoreBadRequest, match="required when CONFIRMED"):
        await create(client, poc_reproduction_steps=None)

    assert core.calls.call_count == 1


async def test_update_sends_only_the_fields_given_and_returns_the_finding(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    patch = core.patch(f"{CP}/findings/5").respond(204)
    core.get(f"{CP}/findings/5").respond(
        200, json=load_fixture("finding_detailed") | {"severity": 6.0}
    )

    result = await finding_service.update_finding(
        client, CTX, 5, severity=6.0, evidence="More evidence", status=None, confidence=None
    )

    assert json.loads(patch.calls.last.request.content) == {
        "severity": 6.0,
        "evidence": "More evidence",
    }
    assert result["id"] == 5
    assert result["severity"] == 6.0


async def test_update_keeps_a_value_of_zero(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    patch = core.patch(f"{CP}/findings/5").respond(204)
    core.get(f"{CP}/findings/5").respond(200, json=load_fixture("finding_detailed"))

    await finding_service.update_finding(client, CTX, 5, severity=0.0, confidence=0.0)

    assert json.loads(patch.calls.last.request.content) == {"severity": 0.0, "confidence": 0.0}


async def test_update_with_nothing_to_change_is_an_error_and_calls_nothing(
    client: CoreClient, core: respx.MockRouter
) -> None:
    with pytest.raises(ToolError, match="at least one field"):
        await finding_service.update_finding(client, CTX, 5, severity=None, evidence=None)

    assert core.calls.call_count == 0


async def test_update_that_worked_but_cannot_be_read_back_is_still_reported(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.patch(f"{CP}/findings/5").respond(204)
    core.get(f"{CP}/findings/5").respond(500, text="down")

    result = await finding_service.update_finding(client, CTX, 5, severity=6.0)

    assert result["findingId"] == 5
    assert result["updated"] is True
    assert "could not be read back" in result["note"]
