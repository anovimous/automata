import json
from collections.abc import AsyncIterator, Callable
from typing import Any
from uuid import UUID

import httpx
import pytest
import respx
from pydantic import BaseModel

from automata_mcp.config import Settings
from automata_mcp.core.client import CoreClient, id_from_location, join_states
from automata_mcp.core.errors import (
    CoreBadRequest,
    CoreConflict,
    CoreNotFound,
    CorePayloadTooLarge,
    CoreResponseError,
    CoreServerError,
    CoreUnprocessable,
    CoreUnreachable,
)
from automata_mcp.core.models.context import (
    AgentContextDetailed,
    HostDetailed,
    ResolvedTarget,
)
from automata_mcp.core.models.enums import (
    FindingCreator,
    FindingStatus,
    HumanVerdict,
    JobState,
)
from automata_mcp.core.models.findings import (
    AgentFindingDetailed,
    AiFindingCreation,
    FindingPatch,
)
from automata_mcp.core.models.jobs import AiJobCreation, HttpJob, RequestJob
from automata_mcp.core.models.reference import (
    RoutineDetailed,
    VulnerabilityDetailed,
    WordlistCreation,
    WordlistDetailed,
)
from automata_mcp.core.models.requests import AiRequestAddition, RequestMetadata, RequestRaw
from automata_mcp.core.models.responses import ResponseMetadata, ResponseRaw
from automata_mcp.core.models.tenants import AgentContextTenant
from automata_mcp.core.paging import Page

BASE = "http://core.test:8080"
CTX = UUID("6f1c0a52-0000-4000-8000-000000000001")
CP = f"/api/agent-contexts/uuid/{CTX}"
PAGE20 = {"page": "0", "size": "20"}
PAGE30 = {"page": "0", "size": "30"}


@pytest.fixture
async def core() -> AsyncIterator[respx.MockRouter]:
    with respx.mock(base_url=BASE, assert_all_called=False) as router:
        yield router


@pytest.fixture
def sleeps() -> list[float]:
    return []


@pytest.fixture
async def client(core: respx.MockRouter, sleeps: list[float]) -> AsyncIterator[CoreClient]:
    async def fake_sleep(seconds: float) -> None:
        sleeps.append(seconds)

    async with CoreClient(BASE, get_retries=2, sleep=fake_sleep) as instance:
        yield instance


def query_of(route: respx.Route) -> dict[str, str]:
    return dict(route.calls.last.request.url.params)


# ---------------------------------------------------------------------------- GET routes

# (method, args, kwargs, path, expected query, fixture, result type)
GET_JSON_CASES: list[tuple[str, tuple[Any, ...], dict[str, Any], str, dict[str, str], str, Any]] = [
    ("get_context", (CTX,), {}, CP, {}, "agent_context_detailed", AgentContextDetailed),
    ("get_host", (CTX,), {}, f"{CP}/host", {}, "host_detailed", HostDetailed),
    (
        "get_request_metadata",
        (CTX, 7),
        {},
        f"{CP}/requests/7/metadata",
        {},
        "request_metadata",
        RequestMetadata,
    ),
    ("get_request_raw", (CTX, 7), {}, f"{CP}/requests/7/raw", {}, "request_raw", RequestRaw),
    (
        "get_response_metadata",
        (CTX, 7),
        {},
        f"{CP}/requests/7/response/metadata",
        {},
        "response_metadata",
        ResponseMetadata,
    ),
    (
        "get_response_raw",
        (CTX, 7),
        {},
        f"{CP}/requests/7/response/raw",
        {},
        "response_raw",
        ResponseRaw,
    ),
    ("get_job", (CTX, 31), {}, f"{CP}/jobs/31", {}, "job_detailed", HttpJob),
    (
        "get_request_job",
        (CTX, 7, 30),
        {},
        f"{CP}/requests/7/jobs/30",
        {},
        "request_job",
        RequestJob,
    ),
    ("get_routine", (3,), {}, "/api/routines/3", {}, "routine_detailed", RoutineDetailed),
    (
        "get_vulnerability",
        (12,),
        {},
        "/api/vulnerabilities/12",
        {},
        "vulnerability_detailed",
        VulnerabilityDetailed,
    ),
    ("get_wordlist", (3,), {}, "/api/wordlists/3", {}, "wordlist_detailed", WordlistDetailed),
    ("get_finding", (CTX, 5), {}, f"{CP}/findings/5", {}, "finding_detailed", AgentFindingDetailed),
]

GET_LIST_CASES: list[tuple[str, str, str, type[BaseModel]]] = [
    ("list_resolved_targets", f"{CP}/resolved-requests", "resolved_targets", ResolvedTarget),
    ("list_tenants", f"{CP}/tenants", "context_tenants", AgentContextTenant),
]

# (method, args, kwargs, path, expected query, fixture)
GET_PAGE_CASES: list[tuple[str, tuple[Any, ...], dict[str, Any], str, dict[str, str], str]] = [
    ("list_context_requests", (CTX,), {}, f"{CP}/requests", PAGE20, "context_requests_page"),
    (
        "list_context_requests",
        (CTX,),
        {"equality_set_id": 55, "page": -2, "size": 500},
        f"{CP}/requests",
        {"equalitySetId": "55", "page": "0", "size": "100"},
        "context_requests_page",
    ),
    ("list_jobs", (CTX,), {}, f"{CP}/jobs", PAGE20, "job_summary_page"),
    (
        "list_jobs",
        (CTX,),
        {"states": [JobState.FINISHED, "FAILED"], "page": 2, "size": 5},
        f"{CP}/jobs",
        {"states": "FINISHED,FAILED", "page": "2", "size": "5"},
        "job_summary_page",
    ),
    (
        "list_request_jobs",
        (CTX, 7),
        {},
        f"{CP}/requests/7/jobs",
        PAGE20,
        "request_job_summary_page",
    ),
    (
        "list_request_jobs",
        (CTX, 7),
        {"states": ["RUNNING"]},
        f"{CP}/requests/7/jobs",
        {"states": "RUNNING", **PAGE20},
        "request_job_summary_page",
    ),
    ("list_routines", (), {}, "/api/routines", {"query": "", **PAGE30}, "routine_summary_page"),
    (
        "list_routines",
        (),
        {"query": "fu", "vulnerability_id": 9, "page": 1, "size": 10},
        "/api/routines",
        {"query": "fu", "vulnId": "9", "page": "1", "size": "10"},
        "routine_summary_page",
    ),
    (
        "list_vulnerabilities",
        (),
        {"parent_id": 11},
        "/api/vulnerabilities",
        {"query": "", "parentVulnId": "11", **PAGE30},
        "vulnerability_summary_page",
    ),
    (
        "list_wordlists",
        (),
        {"query": "ids", "vulnerability_id": 12},
        "/api/wordlists",
        {"query": "ids", "vulnId": "12", **PAGE30},
        "wordlist_summary_page",
    ),
    ("list_findings", (CTX,), {}, f"{CP}/findings", PAGE20, "finding_summary_page"),
    (
        "list_findings",
        (CTX,),
        {
            "request_id": 7,
            "job_id": 31,
            "status": FindingStatus.CONFIRMED,
            "human_verdict": HumanVerdict.TRUE_POSITIVE,
            "created_by": FindingCreator.AI,
        },
        f"{CP}/findings",
        {
            "requestId": "7",
            "jobId": "31",
            "status": "CONFIRMED",
            "humanVerdict": "TRUE_POSITIVE",
            "createdBy": "AI",
            **PAGE20,
        },
        "finding_summary_page",
    ),
]


@pytest.mark.parametrize(
    ("method", "args", "kwargs", "path", "query", "fixture", "result_type"), GET_JSON_CASES
)
async def test_get_routes_return_the_parsed_model(
    client: CoreClient,
    core: respx.MockRouter,
    load_fixture: Callable[[str], Any],
    method: str,
    args: tuple[Any, ...],
    kwargs: dict[str, Any],
    path: str,
    query: dict[str, str],
    fixture: str,
    result_type: type[BaseModel],
) -> None:
    route = core.get(path).respond(200, json=load_fixture(fixture))

    result = await getattr(client, method)(*args, **kwargs)

    assert isinstance(result, result_type)
    assert route.call_count == 1
    assert query_of(route) == query


@pytest.mark.parametrize(("method", "path", "fixture", "item_type"), GET_LIST_CASES)
async def test_list_routes_return_a_list_of_models(
    client: CoreClient,
    core: respx.MockRouter,
    load_fixture: Callable[[str], Any],
    method: str,
    path: str,
    fixture: str,
    item_type: type[BaseModel],
) -> None:
    core.get(path).respond(200, json=load_fixture(fixture))

    result = await getattr(client, method)(CTX)

    assert result
    assert all(isinstance(item, item_type) for item in result)


@pytest.mark.parametrize(("method", "args", "kwargs", "path", "query", "fixture"), GET_PAGE_CASES)
async def test_page_routes_send_clamped_paging_and_filters(
    client: CoreClient,
    core: respx.MockRouter,
    load_fixture: Callable[[str], Any],
    method: str,
    args: tuple[Any, ...],
    kwargs: dict[str, Any],
    path: str,
    query: dict[str, str],
    fixture: str,
) -> None:
    route = core.get(path).respond(200, json=load_fixture(fixture))

    result = await getattr(client, method)(*args, **kwargs)

    assert isinstance(result, Page)
    assert result.content
    assert query_of(route) == query


async def test_the_context_uuid_is_in_the_path(client: CoreClient, core: respx.MockRouter) -> None:
    route = core.get(f"{CP}/host").respond(
        200,
        json={"id": 1, "host": "h", "hostRateLimit": 1, "shortRateLimit": 1, "longRateLimit": 1},
    )

    await client.get_host(CTX)

    assert route.calls.last.request.url.path == f"{CP}/host"


# ---------------------------------------------------------------------------- streams


@pytest.mark.parametrize(
    ("call", "path"),
    [
        (lambda c: c.stream_request_body(CTX, 7), f"{CP}/requests/7/body/stream"),
        (lambda c: c.stream_response_body(CTX, 7), f"{CP}/requests/7/response/body/stream"),
        (lambda c: c.stream_job_result(CTX, 31), f"{CP}/jobs/31/result"),
        (lambda c: c.stream_request_job_result(CTX, 7, 30), f"{CP}/requests/7/jobs/30/result"),
    ],
)
async def test_streams_expose_the_bytes_and_headers(
    client: CoreClient, core: respx.MockRouter, call: Callable[[CoreClient], Any], path: str
) -> None:
    core.get(path).respond(200, content=b'{"a": 1}', headers={"Content-Type": "application/json"})

    async with call(client) as response:
        assert response.headers["content-type"] == "application/json"
        assert await response.aread() == b'{"a": 1}'


async def test_a_stream_can_be_read_in_chunks(client: CoreClient, core: respx.MockRouter) -> None:
    core.get(f"{CP}/jobs/31/result").respond(200, content=b"x" * 100)

    async with client.stream_job_result(CTX, 31) as response:
        first = b""
        async for chunk in response.aiter_bytes(10):
            first = chunk
            break

    assert first == b"x" * 10


async def test_a_stream_error_is_raised_before_any_byte_is_read(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.get(f"{CP}/jobs/31/result").respond(
        409, text="The job has to end before downloading its result"
    )

    with pytest.raises(CoreConflict) as raised:
        async with client.stream_job_result(CTX, 31):
            pytest.fail("the body must not be reachable")

    assert raised.value.core_message == "The job has to end before downloading its result"


async def test_a_stream_of_a_missing_body_is_not_found(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.get(f"{CP}/requests/7/body/stream").respond(404, text="Body not found")

    with pytest.raises(CoreNotFound):
        async with client.stream_request_body(CTX, 7):
            pytest.fail("unreachable")


# ---------------------------------------------------------------------------- jobs (POST)


async def test_create_job_posts_the_body_and_returns_the_id_of_the_location(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    fixture = load_fixture("ai_job_creation")
    route = core.post(f"{CP}/jobs").respond(201, headers={"Location": f"{CP}/jobs/31"})

    job_id = await client.create_job(CTX, AiJobCreation.model_validate(fixture))

    assert job_id == 31
    assert json.loads(route.calls.last.request.content) == fixture


async def test_create_job_without_location_is_a_response_error(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post(f"{CP}/jobs").respond(201)

    with pytest.raises(CoreResponseError, match="Location"):
        await client.create_job(
            CTX, AiJobCreation(request_id=1, ai_description="d", routine_id=1, wordlists_ids=[])
        )


@pytest.mark.parametrize(
    ("method", "suffix", "status"),
    [
        ("queue_job", "queue", 204),
        ("retry_job_preparation", "retry-preparation", 202),
        ("cancel_job", "cancel", 204),
    ],
)
async def test_job_actions_post_without_a_body(
    client: CoreClient, core: respx.MockRouter, method: str, suffix: str, status: int
) -> None:
    route = core.post(f"{CP}/jobs/31/{suffix}").respond(status)

    assert await getattr(client, method)(CTX, 31) is None
    assert route.call_count == 1
    assert route.calls.last.request.content == b""


async def test_set_job_ai_result_posts_the_text(client: CoreClient, core: respx.MockRouter) -> None:
    route = core.post(f"{CP}/jobs/31/ai-result").respond(204)

    await client.set_job_ai_result(CTX, 31, "No difference.")

    assert json.loads(route.calls.last.request.content) == {"aiResult": "No difference."}


async def test_core_messages_of_a_refused_action_are_kept(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post(f"{CP}/jobs/31/queue").respond(409, text="Job 31 is not approved")

    with pytest.raises(CoreConflict) as raised:
        await client.queue_job(CTX, 31)

    assert raised.value.status == 409
    assert "Job 31 is not approved" in str(raised.value)


async def test_a_post_is_never_retried_on_5xx(
    client: CoreClient, core: respx.MockRouter, sleeps: list[float]
) -> None:
    route = core.post(f"{CP}/jobs/31/queue").respond(503, text="down")

    with pytest.raises(CoreServerError):
        await client.queue_job(CTX, 31)

    assert route.call_count == 1
    assert sleeps == []


async def test_a_post_that_cannot_connect_says_it_may_have_been_applied(
    client: CoreClient, core: respx.MockRouter, sleeps: list[float]
) -> None:
    route = core.post(f"{CP}/jobs/31/queue").mock(side_effect=httpx.ConnectError("refused"))

    with pytest.raises(CoreUnreachable, match="may or may not have been applied"):
        await client.queue_job(CTX, 31)

    assert route.call_count == 1
    assert sleeps == []


# ---------------------------------------------------------------------------- wordlists


async def test_create_wordlist_sends_the_file_and_the_metadata_part(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    route = core.post("/api/wordlists").respond(201, headers={"Location": "/api/wordlists/9"})
    metadata = WordlistCreation.model_validate(load_fixture("wordlist_creation"))

    wordlist_id = await client.create_wordlist("a\nb\n", file_name="ids.txt", metadata=metadata)

    request = route.calls.last.request
    assert wordlist_id == 9
    assert request.headers["content-type"].startswith("multipart/form-data")
    body = request.content.decode()
    assert 'name="file"; filename="ids.txt"' in body
    assert "a\nb\n" in body
    assert 'name="metadata"' in body
    assert "Content-Type: application/json" in body
    assert json.dumps(metadata.to_payload()) in body


async def test_create_wordlist_without_metadata_sends_only_the_file(
    client: CoreClient, core: respx.MockRouter
) -> None:
    route = core.post("/api/wordlists").respond(201, headers={"Location": "/api/wordlists/10"})

    await client.create_wordlist(b"x\ny", file_name="w.txt")

    body = route.calls.last.request.content.decode()
    assert 'name="file"' in body
    assert 'name="metadata"' not in body


async def test_a_duplicate_wordlist_is_a_conflict_with_the_core_message(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post("/api/wordlists").respond(409, text="Wordlist already exists with id 3")

    with pytest.raises(CoreConflict, match="already exists with id 3"):
        await client.create_wordlist("a", file_name="w.txt")


async def test_a_too_large_wordlist_is_payload_too_large(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post("/api/wordlists").respond(413, text="The uploaded file is too large")

    with pytest.raises(CorePayloadTooLarge):
        await client.create_wordlist("a", file_name="w.txt")


# ---------------------------------------------------------------------------- findings


async def test_create_finding_posts_and_returns_the_id(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    fixture = load_fixture("ai_finding_creation")
    route = core.post(f"{CP}/findings").respond(201, headers={"Location": f"{CP}/findings/5"})

    finding_id = await client.create_finding(CTX, AiFindingCreation.model_validate(fixture))

    assert finding_id == 5
    assert json.loads(route.calls.last.request.content) == fixture


async def test_update_finding_patches_only_the_given_fields(
    client: CoreClient, core: respx.MockRouter
) -> None:
    route = core.patch(f"{CP}/findings/5").respond(204)

    await client.update_finding(CTX, 5, FindingPatch(severity=3.5, set_ai_description_null=True))

    assert json.loads(route.calls.last.request.content) == {
        "severity": 3.5,
        "setAiDescriptionNull": True,
    }


async def test_update_finding_conflict_is_reported(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.patch(f"{CP}/findings/5").respond(409, text="Finding already reviewed")

    with pytest.raises(CoreConflict):
        await client.update_finding(CTX, 5, FindingPatch(severity=1.0))


# ---------------------------------------------------------------------------- export, ingestion


async def test_export_sends_only_the_host_id_and_drops_blank_lines(
    client: CoreClient, core: respx.MockRouter
) -> None:
    route = core.get("/api/export/body-properties").respond(
        200, text="id\nname\n\n  \nemail\n", headers={"Content-Type": "text/plain"}
    )

    names = await client.export_body_property_names(2)

    assert names == ["id", "name", "email"]
    assert query_of(route) == {"hostId": "2"}


async def test_add_request_posts_the_body_and_returns_the_summary(
    client: CoreClient, core: respx.MockRouter, load_fixture: Callable[[str], Any]
) -> None:
    fixture = load_fixture("ai_request_addition")
    route = core.post("/api/external/ai/requests").respond(
        201, json=load_fixture("request_summary")
    )

    summary = await client.add_request(AiRequestAddition.model_validate(fixture))

    assert summary.request_id == 31
    assert summary.source == "AI"
    assert json.loads(route.calls.last.request.content) == fixture


async def test_add_request_with_an_unknown_host_is_not_found(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post("/api/external/ai/requests").respond(404, text="Host not found")

    with pytest.raises(CoreNotFound, match="Host not found"):
        await client.add_request(AiRequestAddition(request_base64="QQ==", host="nope"))


async def test_add_request_with_an_unexpected_answer_is_a_response_error(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post("/api/external/ai/requests").respond(201, json={"unexpected": True})

    with pytest.raises(CoreResponseError):
        await client.add_request(AiRequestAddition(request_base64="QQ==", host="h"))


# ---------------------------------------------------------------------------- errors and retries


@pytest.mark.parametrize(
    ("status", "error_type"),
    [(404, CoreNotFound), (400, CoreBadRequest), (409, CoreConflict), (422, CoreUnprocessable)],
)
async def test_client_errors_are_raised_without_retry(
    client: CoreClient,
    core: respx.MockRouter,
    sleeps: list[float],
    status: int,
    error_type: type[Exception],
) -> None:
    route = core.get(f"{CP}/host").respond(status, text="because")

    with pytest.raises(error_type, match="because"):
        await client.get_host(CTX)

    assert route.call_count == 1
    assert sleeps == []


async def test_a_get_is_retried_on_5xx_and_then_succeeds(
    client: CoreClient,
    core: respx.MockRouter,
    sleeps: list[float],
    load_fixture: Callable[[str], Any],
) -> None:
    route = core.get(f"{CP}/host").mock(
        side_effect=[
            httpx.Response(503, text="down"),
            httpx.Response(200, json=load_fixture("host_detailed")),
        ]
    )

    host = await client.get_host(CTX)

    assert host.host == "api.example.com"
    assert route.call_count == 2
    assert sleeps == [0.5]


async def test_a_get_gives_up_after_the_configured_retries(
    client: CoreClient, core: respx.MockRouter, sleeps: list[float]
) -> None:
    route = core.get(f"{CP}/host").respond(500, text="boom")

    with pytest.raises(CoreServerError, match="boom"):
        await client.get_host(CTX)

    assert route.call_count == 3
    assert sleeps == [0.5, 1.0]


async def test_a_get_is_retried_on_connect_errors_and_timeouts(
    client: CoreClient,
    core: respx.MockRouter,
    sleeps: list[float],
    load_fixture: Callable[[str], Any],
) -> None:
    route = core.get(f"{CP}/host").mock(
        side_effect=[
            httpx.ConnectError("refused"),
            httpx.ReadTimeout("slow"),
            httpx.Response(200, json=load_fixture("host_detailed")),
        ]
    )

    await client.get_host(CTX)

    assert route.call_count == 3
    assert sleeps == [0.5, 1.0]


async def test_a_get_that_never_connects_reports_the_core_unreachable(
    client: CoreClient, core: respx.MockRouter
) -> None:
    route = core.get(f"{CP}/host").mock(side_effect=httpx.ConnectError("refused"))

    with pytest.raises(CoreUnreachable) as raised:
        await client.get_host(CTX)

    assert route.call_count == 3
    assert BASE in str(raised.value)
    assert "may or may not" not in str(raised.value)


async def test_other_transport_errors_are_not_retried(
    client: CoreClient, core: respx.MockRouter
) -> None:
    route = core.get(f"{CP}/host").mock(side_effect=httpx.RemoteProtocolError("bad"))

    with pytest.raises(CoreUnreachable, match="RemoteProtocolError"):
        await client.get_host(CTX)

    assert route.call_count == 1


async def test_zero_retries_means_one_attempt(core: respx.MockRouter) -> None:
    route = core.get(f"{CP}/host").respond(503, text="down")

    async with CoreClient(BASE, get_retries=0) as no_retry:
        with pytest.raises(CoreServerError):
            await no_retry.get_host(CTX)

    assert route.call_count == 1


async def test_a_body_that_is_not_json_is_a_response_error(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.get(f"{CP}/host").respond(200, text="<html>oops</html>")

    with pytest.raises(CoreResponseError, match="not JSON"):
        await client.get_host(CTX)


async def test_a_payload_of_the_wrong_shape_is_a_response_error(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.get(f"{CP}/host").respond(200, json={"nothing": "useful"})

    with pytest.raises(CoreResponseError, match="unexpected shape"):
        await client.get_host(CTX)


async def test_a_list_route_answering_an_object_is_a_response_error(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.get(f"{CP}/tenants").respond(200, json={"content": []})

    with pytest.raises(CoreResponseError, match="not a list"):
        await client.list_tenants(CTX)


async def test_a_page_route_answering_a_list_is_a_response_error(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.get(f"{CP}/jobs").respond(200, json=[])

    with pytest.raises(CoreResponseError, match="page"):
        await client.list_jobs(CTX)


# ---------------------------------------------------------------------------- helpers


@pytest.mark.parametrize(
    ("location", "expected"),
    [
        (f"{CP}/jobs/31", 31),
        ("/api/wordlists/9", 9),
        ("/api/wordlists/9/", 9),
        ("http://core:8080/api/x/12?y=1", 12),
    ],
)
def test_id_from_location(location: str, expected: int) -> None:
    assert id_from_location(httpx.Response(201, headers={"Location": location})) == expected


@pytest.mark.parametrize("headers", [{}, {"Location": "/api/wordlists/abc"}])
def test_id_from_location_rejects_a_missing_or_idless_header(headers: dict[str, str]) -> None:
    with pytest.raises(CoreResponseError):
        id_from_location(httpx.Response(201, headers=headers))


def test_join_states() -> None:
    assert join_states(None) is None
    assert join_states([]) is None
    assert join_states([JobState.FINISHED, "FAILED"]) == "FINISHED,FAILED"


async def test_from_settings_uses_the_core_url(core: respx.MockRouter) -> None:
    settings = Settings(_env_file=None, core_url=f"{BASE}/", get_retries=0)  # type: ignore[call-arg]
    route = core.get(f"{CP}/host").respond(500, text="x")

    async with CoreClient.from_settings(settings) as built:
        assert built.base_url == BASE
        with pytest.raises(CoreServerError):
            await built.get_host(CTX)

    assert route.call_count == 1


async def test_the_context_manager_closes_the_http_client(core: respx.MockRouter) -> None:
    async with CoreClient(BASE) as closing:
        assert not closing._http.is_closed

    assert closing._http.is_closed
