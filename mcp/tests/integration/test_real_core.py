"""One real flow per group of tools, and the three things first read only in the core's code.

Each test uses the context the ``seed`` fixture made. They are written to be independent of each
other: a test creates what it needs (with unique names) and does not rely on the order.
"""

import uuid
from typing import Any

import httpx
import pytest
from mcp.types import CallToolResult

from conftest import CORE_URL, Seed, b64
from helpers import Agent

pytestmark = pytest.mark.integration


def text_of(result: CallToolResult) -> str:
    return result.content[0].text  # type: ignore[union-attr]


async def call(agent: Agent, seed: Seed, tool: str, **arguments: Any) -> CallToolResult:
    return await agent.call_tool(tool, {"context_uuid": seed.context_uuid} | arguments)


async def ok(agent: Agent, seed: Seed, tool: str, **arguments: Any) -> dict[str, Any]:
    result = await call(agent, seed, tool, **arguments)
    assert not result.is_error, text_of(result)
    return result.structured_content or {}


async def make_job(agent: Agent, seed: Seed, **extra: Any) -> int:
    """A DRAFT job on the seeded request (the context does not auto-approve)."""
    extra.setdefault("custom_config", {"flags": []})  # the seeded routine's schema wants an object
    data = await ok(
        agent,
        seed,
        "create_job",
        request_id=seed.request_id,
        routine_id=seed.routine_id,
        ai_description="Integration test job",
        wordlist_ids=[],
        **extra,
    )
    return int(data["jobId"])


# ---------------------------------------------------------------------------- context


async def test_the_briefing_describes_the_seeded_context(real_agent: Agent, seed: Seed) -> None:
    data = await ok(real_agent, seed, "get_context_briefing")

    assert data["context"]["uuid"] == seed.context_uuid
    assert data["context"]["representativeStrategy"] == "LATEST"
    assert {t["requestId"] for t in data["targets"]} == {seed.request_id, seed.other_request_id}
    assert data["tenants"][0]["tenantId"] == seed.tenant_id
    assert data["allowedRoutines"][0]["id"] == seed.routine_id
    assert data["allowedRoutines"][0]["configSchema"]["type"] == "object"
    assert data["host"]["host"] == seed.host_name
    assert isinstance(data["recentJobs"], list)
    assert "total" in data["recentFindings"]


async def test_context_host_targets_and_tenants(real_agent: Agent, seed: Seed) -> None:
    context = await ok(real_agent, seed, "get_context")
    host = await ok(real_agent, seed, "get_host")
    targets = await ok(real_agent, seed, "list_targets")
    tenants = await ok(real_agent, seed, "list_tenants")

    assert context["hostId"] == seed.host_id
    assert host["hostRateLimit"] == 50
    assert targets["total"] == 2
    assert targets["enriched"] == 2
    assert [t["tenantId"] for t in tenants["tenants"]] == [seed.tenant_id]


async def test_an_unknown_context_is_refused_with_the_guidance(real_agent: Agent) -> None:
    result = await real_agent.call_tool("get_context", {"context_uuid": str(uuid.uuid4())})

    assert result.is_error
    assert "Not found, or not part of this context" in text_of(result)


# --- requests and responses


async def test_a_request_and_its_response_are_readable(real_agent: Agent, seed: Seed) -> None:
    request = await ok(real_agent, seed, "get_request", request_id=seed.request_id)
    response = await ok(real_agent, seed, "get_response", request_id=seed.request_id)
    raw = await ok(real_agent, seed, "get_request_raw", request_id=seed.request_id)
    raw_response = await ok(real_agent, seed, "get_response_raw", request_id=seed.request_id)

    assert request["line"]["method"] == "POST"
    assert request["body"]["text"] == '{"email":"a@b.test","role":"x"}'
    assert response["statusCode"] == 200
    assert response["body"]["text"] == '{"id":42,"ok":1}'
    assert raw["headText"].startswith("POST /api/users/42 HTTP/1.1\r\n")
    assert raw["rawRequestText"].endswith('{"email":"a@b.test","role":"x"}')
    assert raw_response["bodyText"] == '{"id":42,"ok":1}'


async def test_bodies_can_be_read_in_parts(real_agent: Agent, seed: Seed) -> None:
    part = await ok(
        real_agent, seed, "get_request_body", request_id=seed.request_id, offset=2, max_bytes=5
    )
    body = await ok(real_agent, seed, "get_response_body", request_id=seed.request_id)

    assert part["text"] == "email"
    assert part["nextOffset"] == 7
    assert body["text"] == '{"id":42,"ok":1}'


async def test_a_request_without_a_body_or_a_response(real_agent: Agent, seed: Seed) -> None:
    request = await ok(real_agent, seed, "get_request", request_id=seed.other_request_id)
    raw = await ok(real_agent, seed, "get_request_raw", request_id=seed.other_request_id)
    missing = await call(real_agent, seed, "get_response", request_id=seed.other_request_id)

    assert request["body"]["hasBody"] is False
    assert raw["rawRequestText"] == raw["headText"]
    assert missing.is_error
    assert "no stored response" in text_of(missing)


async def test_the_requests_of_the_context_are_listed(real_agent: Agent, seed: Seed) -> None:
    data = await ok(real_agent, seed, "list_context_requests")

    assert {r["requestId"] for r in data["requests"]} == {seed.request_id, seed.other_request_id}
    assert all(r["resolvedTarget"] for r in data["requests"])


# --- reference and wordlists


@pytest.mark.xfail(
    reason="core bug: VulnerabilityMapper dereferences getParent() of a root vulnerability (500)",
    strict=False,
)
async def test_a_root_vulnerability_can_be_read(real_agent: Agent, seed: Seed) -> None:
    async with httpx.AsyncClient(base_url=CORE_URL, timeout=30) as core:
        created = await core.post(
            "/api/vulnerabilities", json={"name": f"IT-root-{uuid.uuid4().hex[:8]}"}
        )
    root_id = int(created.headers["Location"].rsplit("/", 1)[-1])

    data = await ok(real_agent, seed, "get_vulnerability", vulnerability_id=root_id)

    assert data["id"] == root_id
    assert "parentId" not in data


async def test_reference_data_is_readable(real_agent: Agent, seed: Seed) -> None:
    routines = await ok(real_agent, seed, "list_routines", query=seed.suffix)
    routine = await ok(real_agent, seed, "get_routine", routine_id=seed.routine_id)
    vulnerabilities = await ok(real_agent, seed, "list_vulnerabilities", query=seed.suffix)
    vulnerability = await ok(
        real_agent, seed, "get_vulnerability", vulnerability_id=seed.vulnerability_id
    )

    assert [r["id"] for r in routines["routines"]] == [seed.routine_id]
    assert routine["wordlistRequired"] is True
    assert routine["configSchema"]["properties"]["flags"] == {"type": "array"}
    assert [v["id"] for v in vulnerabilities["vulnerabilities"]] == [seed.vulnerability_id]
    assert vulnerability["name"] == f"IT BOLA {seed.suffix}"
    assert vulnerability["parentId"] == seed.parent_vulnerability_id


async def test_a_wordlist_is_uploaded_with_its_metadata_and_a_duplicate_is_refused(
    real_agent: Agent, seed: Seed
) -> None:
    name = f"it-words-{uuid.uuid4().hex[:8]}.txt"
    content = f"alpha-{uuid.uuid4().hex}\nbeta\ngamma\n"

    created = await ok(
        real_agent,
        seed,
        "create_wordlist",
        name=name,
        content=content,
        description="Integration test list",
        vulnerability_ids=[seed.vulnerability_id],
    )
    stored = await ok(real_agent, seed, "get_wordlist", wordlist_id=created["wordlistId"])
    listed = await ok(
        real_agent, seed, "list_wordlists", query=name, vulnerability_id=seed.vulnerability_id
    )
    again = await call(real_agent, seed, "create_wordlist", name=name + ".copy", content=content)

    assert stored["name"] == name
    assert stored["description"] == "Integration test list"
    assert stored["numberOfLines"] == 3
    assert [w["id"] for w in listed["wordlists"]] == [created["wordlistId"]]
    assert again.is_error
    assert "use it instead of creating another" in text_of(again)


# ---------------------------------------------------------------------------- jobs


async def test_a_job_is_created_as_a_draft_that_waits_for_a_human(
    real_agent: Agent, seed: Seed
) -> None:
    job_id = await make_job(real_agent, seed, rate=5)
    job = await ok(real_agent, seed, "get_job", job_id=job_id)

    assert job["currentState"] == "DRAFT"
    assert job["approved"] is False
    assert job["waitingForAction"] is True
    assert job["terminal"] is False
    assert job["routine"]["id"] == seed.routine_id
    assert job["rate"] == 5


async def test_the_state_filter_is_accepted_as_a_comma_separated_list(
    real_agent: Agent, seed: Seed
) -> None:
    job_id = await make_job(real_agent, seed)

    both = await ok(real_agent, seed, "list_jobs", states=["DRAFT", "FAILED"])
    only_failed = await ok(real_agent, seed, "list_jobs", states=["FAILED"])
    history = await ok(
        real_agent,
        seed,
        "list_request_jobs",
        request_id=seed.request_id,
        states=["DRAFT", "FAILED"],
    )

    assert job_id in [j["id"] for j in both["jobs"]]
    assert job_id not in [j["id"] for j in only_failed["jobs"]]
    assert job_id in [j["id"] for j in history["jobs"]]
    assert next(j for j in history["jobs"] if j["id"] == job_id)["origin"] == "THIS_CONTEXT"


async def test_the_history_of_a_request_shows_what_a_job_did(real_agent: Agent, seed: Seed) -> None:
    job_id = await make_job(real_agent, seed, verbosity="NORMAL")

    job = await ok(real_agent, seed, "get_request_job", request_id=seed.request_id, job_id=job_id)

    assert job["origin"] == "THIS_CONTEXT"
    assert job["verbosity"] == "NORMAL"
    assert job["aiDescription"] == "Integration test job"


async def test_a_raw_request_text_in_the_config_reaches_the_core_as_base64(
    real_agent: Agent, seed: Seed
) -> None:
    job_id = await make_job(
        real_agent,
        seed,
        custom_config={
            "rawRequestText": f"GET /api/users/FUZZ HTTP/1.1\nHost: {seed.host_name}\n\n"
        },
    )

    job = await ok(real_agent, seed, "get_job", job_id=job_id)

    assert "rawRequestText" not in job["customConfig"]
    assert job["customConfig"]["rawRequestBase64"] == b64(
        f"GET /api/users/FUZZ HTTP/1.1\r\nHost: {seed.host_name}\r\n\r\n"
    )


async def test_the_core_refuses_what_the_server_does_not_check(
    real_agent: Agent, seed: Seed
) -> None:
    unknown_request = await call(
        real_agent,
        seed,
        "create_job",
        request_id=999_999_999,
        routine_id=seed.routine_id,
        ai_description="x",
        wordlist_ids=[],
    )
    bad_config = await call(
        real_agent,
        seed,
        "create_job",
        request_id=seed.request_id,
        routine_id=seed.routine_id,
        ai_description="x",
        wordlist_ids=[],
        custom_config={"flags": "not-an-array"},
    )

    assert unknown_request.is_error
    assert "call list_targets" in text_of(unknown_request)
    assert bad_config.is_error
    assert "The core rejected the arguments" in text_of(bad_config)


async def test_a_job_nobody_approved_cannot_be_queued_or_finished_or_canceled(
    real_agent: Agent, seed: Seed
) -> None:
    job_id = await make_job(real_agent, seed)

    queued = await call(real_agent, seed, "queue_job", job_id=job_id)
    result = await call(real_agent, seed, "get_job_result", job_id=job_id)
    concluded = await call(
        real_agent, seed, "set_job_ai_result", job_id=job_id, ai_result="too early"
    )
    canceled = await call(real_agent, seed, "cancel_job", job_id=job_id)

    assert queued.is_error and "a human must approve it" in text_of(queued)
    assert result.is_error and "use wait_for_jobs" in text_of(result)
    assert concluded.is_error and "use wait_for_jobs" in text_of(concluded)
    assert canceled.is_error and "TOQUEUE, QUEUED, RUNNING or PAUSED" in text_of(canceled)


async def test_waiting_for_a_job_that_waits_for_a_human_returns_at_once(
    real_agent: Agent, seed: Seed
) -> None:
    job_id = await make_job(real_agent, seed)

    data = await ok(real_agent, seed, "wait_for_jobs", job_ids=[job_id], timeout_seconds=20)

    assert data["timedOut"] is False
    assert data["waitingForActionJobIds"] == [job_id]
    assert "waits for someone" in data["next_steps"][0]


async def test_retrying_the_preparation_of_a_draft_is_accepted_or_explained(
    real_agent: Agent, seed: Seed
) -> None:
    job_id = await make_job(real_agent, seed)

    result = await call(real_agent, seed, "retry_job_preparation", job_id=job_id)

    assert result.is_error is False or "Only a DRAFT job" in text_of(result)


# ---------------------------------------------------------------------------- findings


async def test_a_finding_is_created_read_and_updated_field_by_field(
    real_agent: Agent, seed: Seed
) -> None:
    created = await ok(
        real_agent,
        seed,
        "create_finding",
        request_id=seed.request_id,
        vulnerability_id=seed.vulnerability_id,
        severity=7.5,
        confidence=6.0,
        status="SUGGESTED",
        evidence="Tenant A could read tenant B",
        ai_description="BOLA",
    )
    finding_id = created["findingId"]

    before = await ok(real_agent, seed, "get_finding", finding_id=finding_id)
    after = await ok(real_agent, seed, "update_finding", finding_id=finding_id, severity=4.0)

    assert before["origin"] == "THIS_CONTEXT"
    assert before["createdBy"] == "AI"
    assert after["severity"] == 4.0
    # A PATCH that leaves a field out must leave it as it was.
    assert after["confidence"] == 6.0
    assert after["evidence"] == "Tenant A could read tenant B"
    assert after["aiDescription"] == "BOLA"
    assert after["status"] == "SUGGESTED"


async def test_findings_are_listed_with_their_filters(real_agent: Agent, seed: Seed) -> None:
    created = await ok(
        real_agent,
        seed,
        "create_finding",
        request_id=seed.other_request_id,
        vulnerability_id=seed.vulnerability_id,
        severity=3.0,
        confidence=3.0,
        status="SUGGESTED",
        evidence="listing test",
    )

    listed = await ok(
        real_agent,
        seed,
        "list_findings",
        request_id=seed.other_request_id,
        status="SUGGESTED",
        created_by="AI",
    )
    none = await ok(
        real_agent, seed, "list_findings", status="CONFIRMED", human_verdict="TRUE_POSITIVE"
    )

    assert created["findingId"] in [f["id"] for f in listed["findings"]]
    assert all(f["requestId"] == seed.other_request_id for f in listed["findings"])
    assert none["findings"] == []


async def test_a_confirmed_finding_needs_steps_and_a_request_outside_the_context_is_refused(
    real_agent: Agent, seed: Seed
) -> None:
    no_steps = await call(
        real_agent,
        seed,
        "create_finding",
        request_id=seed.request_id,
        vulnerability_id=seed.vulnerability_id,
        severity=8.0,
        confidence=8.0,
        status="CONFIRMED",
        evidence="e",
    )
    outside = await call(
        real_agent,
        seed,
        "create_finding",
        request_id=999_999_999,
        vulnerability_id=seed.vulnerability_id,
        severity=1.0,
        confidence=1.0,
        status="SUGGESTED",
        evidence="e",
    )

    assert no_steps.is_error
    assert "The core rejected the arguments" in text_of(no_steps)
    assert outside.is_error
    assert "not readable in this context" in text_of(outside)


# ---------------------------------------------------------------------------- ingestion and export


async def test_an_observed_request_is_stored_but_not_part_of_the_context(
    real_agent: Agent, seed: Seed
) -> None:
    stored = await ok(
        real_agent,
        seed,
        "add_request",
        host=seed.host_name,
        raw_request=(
            f"PUT /api/profile HTTP/1.1\nHost: {seed.host_name}\n"
            'Content-Type: application/json\n\n{"nickname":"a","avatar":"b"}'
        ),
        raw_response="HTTP/1.1 204 No Content\n\n",
        tenant_id=seed.tenant_id,
        context_uuid=seed.context_uuid,
    )
    read_back = await call(real_agent, seed, "get_request", request_id=stored["requestId"])

    assert stored["source"] == "AI"
    assert stored["method"] == "PUT"
    assert "until a human adds it to a context" in stored["note"]
    assert read_back.is_error


async def test_an_unknown_host_is_refused(real_agent: Agent, seed: Seed) -> None:
    result = await real_agent.call_tool(
        "add_request",
        {"host": f"nope-{seed.suffix}.example.com", "raw_request": "GET / HTTP/1.1\nHost: x\n\n"},
    )

    assert result.is_error
    assert "hosts are managed by humans" in text_of(result)


async def test_the_field_names_of_the_host_are_exported(real_agent: Agent, seed: Seed) -> None:
    data = await ok(real_agent, seed, "export_body_property_names")

    assert {"email", "role"} <= set(data["names"])
    assert data["truncated"] is False
    assert data["total"] == len(data["names"])
