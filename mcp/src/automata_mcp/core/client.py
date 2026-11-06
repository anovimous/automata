"""The REST client of the core: one method per route of the plan (section 6.2).

The client translates and nothing else: no rule of the core is repeated here. Errors become
``CoreError`` subclasses (``core/errors.py``). GETs are retried on connect errors, timeouts and 5xx;
POST and PATCH never are. Bodies and results are exposed as streams so the caller can cap them.
"""

import asyncio
import json
import logging
from collections.abc import AsyncIterator, Awaitable, Callable, Mapping, Sequence
from contextlib import AbstractAsyncContextManager, asynccontextmanager
from types import TracebackType
from typing import Any, Self
from uuid import UUID

import httpx
from pydantic import BaseModel, ValidationError

from automata_mcp.config import Settings
from automata_mcp.core.errors import CoreResponseError, error_from_response, unreachable
from automata_mcp.core.models.context import (
    AgentContextDetailed,
    ContextRequestSummary,
    HostDetailed,
    ResolvedTarget,
)
from automata_mcp.core.models.enums import FindingCreator, FindingStatus, HumanVerdict, JobState
from automata_mcp.core.models.findings import (
    AgentFindingDetailed,
    AgentFindingSummary,
    AiFindingCreation,
    FindingPatch,
)
from automata_mcp.core.models.jobs import (
    AiJobCreation,
    AiResult,
    HttpJob,
    HttpJobSummary,
    RequestJob,
    RequestJobSummary,
)
from automata_mcp.core.models.reference import (
    RoutineDetailed,
    RoutineSummary,
    VulnerabilityDetailed,
    VulnerabilitySummary,
    WordlistCreation,
    WordlistDetailed,
    WordlistSummary,
)
from automata_mcp.core.models.requests import (
    AiRequestAddition,
    RequestMetadata,
    RequestRaw,
    RequestSummary,
)
from automata_mcp.core.models.responses import ResponseMetadata, ResponseRaw
from automata_mcp.core.models.tenants import AgentContextTenant
from automata_mcp.core.paging import DEFAULT_PAGE_SIZE, Page, drop_none, page_params

logger = logging.getLogger(__name__)

CONNECT_TIMEOUT_SECONDS = 5.0
BACKOFF_SECONDS = 0.5

type Sleep = Callable[[float], Awaitable[None]]
type Stream = AbstractAsyncContextManager[httpx.Response]


def id_from_location(response: httpx.Response) -> int:
    """The id at the end of the ``Location`` header of a creation answer."""
    location = response.headers.get("Location")
    if not location:
        raise CoreResponseError("The core created the item but sent no Location header.")
    last = location.split("?", 1)[0].rstrip("/").rsplit("/", 1)[-1]
    try:
        return int(last)
    except ValueError:
        raise CoreResponseError(
            f"The core sent a Location header without an id: {location}"
        ) from None


def join_states(states: Sequence[JobState | str] | None) -> str | None:
    """The ``states`` query value: a comma-separated list, or None when there is none."""
    return ",".join(str(state) for state in states) if states else None


class CoreClient:
    def __init__(
        self,
        base_url: str,
        *,
        timeout_seconds: float = 30.0,
        get_retries: int = 2,
        transport: httpx.AsyncBaseTransport | None = None,
        sleep: Sleep = asyncio.sleep,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._get_retries = max(get_retries, 0)
        self._sleep = sleep
        self._http = httpx.AsyncClient(
            base_url=self._base_url,
            timeout=httpx.Timeout(timeout_seconds, connect=CONNECT_TIMEOUT_SECONDS),
            transport=transport,
        )

    @classmethod
    def from_settings(
        cls, settings: Settings, *, transport: httpx.AsyncBaseTransport | None = None
    ) -> Self:
        return cls(
            settings.core_url,
            timeout_seconds=settings.request_timeout_seconds,
            get_retries=settings.get_retries,
            transport=transport,
        )

    @property
    def base_url(self) -> str:
        return self._base_url

    async def aclose(self) -> None:
        await self._http.aclose()

    async def __aenter__(self) -> Self:
        return self

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        await self.aclose()

    # ------------------------------------------------------------------ transport

    async def _send(
        self,
        method: str,
        path: str,
        *,
        params: Mapping[str, Any] | None = None,
        json_body: Any = None,
        files: Mapping[str, Any] | None = None,
        stream: bool = False,
    ) -> httpx.Response:
        """Send a request and return the answer, or raise the ``CoreError`` it maps to.

        With ``stream`` the body is not read; the caller must close the response.
        """
        request = self._http.build_request(
            method,
            path,
            params=drop_none(dict(params)) if params else None,
            json=json_body,
            files=files,
        )
        attempts = self._get_retries + 1 if method == "GET" else 1
        for attempt in range(attempts):
            last = attempt == attempts - 1
            try:
                response = await self._http.send(request, stream=stream)
            except (httpx.ConnectError, httpx.TimeoutException) as exc:
                if last:
                    raise unreachable(
                        self._base_url, type(exc).__name__, may_have_been_applied=method != "GET"
                    ) from exc
                logger.warning("%s %s failed (%s), retrying", method, path, type(exc).__name__)
                await self._sleep(BACKOFF_SECONDS * 2**attempt)
                continue
            except httpx.TransportError as exc:
                raise unreachable(
                    self._base_url, type(exc).__name__, may_have_been_applied=method != "GET"
                ) from exc
            if response.status_code >= 500 and not last and method == "GET":
                logger.warning("%s %s answered %s, retrying", method, path, response.status_code)
                await response.aclose()
                await self._sleep(BACKOFF_SECONDS * 2**attempt)
                continue
            break
        if response.status_code >= 400:
            try:
                await response.aread()
                body = response.text
            finally:
                await response.aclose()
            raise error_from_response(response.status_code, body)
        return response

    async def _json(self, method: str, path: str, **kwargs: Any) -> Any:
        response = await self._send(method, path, **kwargs)
        try:
            return response.json()
        except ValueError:
            raise CoreResponseError(
                f"The core answered {method} {path} with a body that is not JSON."
            ) from None

    async def _get_model[M: BaseModel](
        self, path: str, model: type[M], params: Mapping[str, Any] | None = None
    ) -> M:
        return self._parse(model, await self._json("GET", path, params=params), path)

    async def _get_list[M: BaseModel](self, path: str, model: type[M]) -> list[M]:
        data = await self._json("GET", path)
        if not isinstance(data, list):
            raise CoreResponseError(
                f"The core answered GET {path} with something that is not a list."
            )
        return [self._parse(model, item, path) for item in data]

    async def _get_page[M: BaseModel](
        self, path: str, model: type[M], params: Mapping[str, Any]
    ) -> Page[M]:
        data = await self._json("GET", path, params=params)
        try:
            return Page[model].model_validate(data)  # type: ignore[valid-type]
        except ValidationError as exc:
            raise CoreResponseError(
                f"The core answered GET {path} with a page of an unexpected shape: {exc}"
            ) from exc

    @staticmethod
    def _parse[M: BaseModel](model: type[M], data: Any, path: str) -> M:
        try:
            return model.model_validate(data)
        except ValidationError as exc:
            raise CoreResponseError(
                f"The core answered GET {path} with a payload of an unexpected shape: {exc}"
            ) from exc

    @asynccontextmanager
    async def _stream(self, path: str) -> AsyncIterator[httpx.Response]:
        response = await self._send("GET", path, stream=True)
        try:
            yield response
        finally:
            await response.aclose()

    @staticmethod
    def _context(context_uuid: UUID) -> str:
        return f"/api/agent-contexts/uuid/{context_uuid}"

    # ------------------------------------------------------------------ context

    async def get_context(self, context_uuid: UUID) -> AgentContextDetailed:
        return await self._get_model(self._context(context_uuid), AgentContextDetailed)

    async def list_resolved_targets(self, context_uuid: UUID) -> list[ResolvedTarget]:
        return await self._get_list(
            f"{self._context(context_uuid)}/resolved-requests", ResolvedTarget
        )

    async def get_host(self, context_uuid: UUID) -> HostDetailed:
        return await self._get_model(f"{self._context(context_uuid)}/host", HostDetailed)

    async def list_tenants(self, context_uuid: UUID) -> list[AgentContextTenant]:
        return await self._get_list(f"{self._context(context_uuid)}/tenants", AgentContextTenant)

    async def list_context_requests(
        self,
        context_uuid: UUID,
        *,
        equality_set_id: int | None = None,
        page: int = 0,
        size: int = DEFAULT_PAGE_SIZE,
    ) -> Page[ContextRequestSummary]:
        params = {"equalitySetId": equality_set_id, **page_params(page, size)}
        return await self._get_page(
            f"{self._context(context_uuid)}/requests", ContextRequestSummary, params
        )

    # ------------------------------------------------------------------ requests and responses

    async def get_request_metadata(self, context_uuid: UUID, request_id: int) -> RequestMetadata:
        return await self._get_model(
            f"{self._context(context_uuid)}/requests/{request_id}/metadata", RequestMetadata
        )

    async def get_request_raw(self, context_uuid: UUID, request_id: int) -> RequestRaw:
        return await self._get_model(
            f"{self._context(context_uuid)}/requests/{request_id}/raw", RequestRaw
        )

    def stream_request_body(self, context_uuid: UUID, request_id: int) -> Stream:
        return self._stream(f"{self._context(context_uuid)}/requests/{request_id}/body/stream")

    async def get_response_metadata(self, context_uuid: UUID, request_id: int) -> ResponseMetadata:
        return await self._get_model(
            f"{self._context(context_uuid)}/requests/{request_id}/response/metadata",
            ResponseMetadata,
        )

    async def get_response_raw(self, context_uuid: UUID, request_id: int) -> ResponseRaw:
        return await self._get_model(
            f"{self._context(context_uuid)}/requests/{request_id}/response/raw", ResponseRaw
        )

    def stream_response_body(self, context_uuid: UUID, request_id: int) -> Stream:
        return self._stream(
            f"{self._context(context_uuid)}/requests/{request_id}/response/body/stream"
        )

    # ------------------------------------------------------------------ jobs

    async def create_job(self, context_uuid: UUID, job: AiJobCreation) -> int:
        """Create the job; returns its id (from the ``Location`` header)."""
        response = await self._send(
            "POST", f"{self._context(context_uuid)}/jobs", json_body=job.to_payload()
        )
        return id_from_location(response)

    async def list_jobs(
        self,
        context_uuid: UUID,
        *,
        states: Sequence[JobState | str] | None = None,
        page: int = 0,
        size: int = DEFAULT_PAGE_SIZE,
    ) -> Page[HttpJobSummary]:
        params = {"states": join_states(states), **page_params(page, size)}
        return await self._get_page(f"{self._context(context_uuid)}/jobs", HttpJobSummary, params)

    async def get_job(self, context_uuid: UUID, job_id: int) -> HttpJob:
        return await self._get_model(f"{self._context(context_uuid)}/jobs/{job_id}", HttpJob)

    def stream_job_result(self, context_uuid: UUID, job_id: int) -> Stream:
        return self._stream(f"{self._context(context_uuid)}/jobs/{job_id}/result")

    async def queue_job(self, context_uuid: UUID, job_id: int) -> None:
        await self._send("POST", f"{self._context(context_uuid)}/jobs/{job_id}/queue")

    async def retry_job_preparation(self, context_uuid: UUID, job_id: int) -> None:
        await self._send("POST", f"{self._context(context_uuid)}/jobs/{job_id}/retry-preparation")

    async def set_job_ai_result(self, context_uuid: UUID, job_id: int, ai_result: str) -> None:
        await self._send(
            "POST",
            f"{self._context(context_uuid)}/jobs/{job_id}/ai-result",
            json_body=AiResult(ai_result=ai_result).to_payload(),
        )

    async def cancel_job(self, context_uuid: UUID, job_id: int) -> None:
        await self._send("POST", f"{self._context(context_uuid)}/jobs/{job_id}/cancel")

    async def list_request_jobs(
        self,
        context_uuid: UUID,
        request_id: int,
        *,
        states: Sequence[JobState | str] | None = None,
        page: int = 0,
        size: int = DEFAULT_PAGE_SIZE,
    ) -> Page[RequestJobSummary]:
        params = {"states": join_states(states), **page_params(page, size)}
        return await self._get_page(
            f"{self._context(context_uuid)}/requests/{request_id}/jobs", RequestJobSummary, params
        )

    async def get_request_job(self, context_uuid: UUID, request_id: int, job_id: int) -> RequestJob:
        return await self._get_model(
            f"{self._context(context_uuid)}/requests/{request_id}/jobs/{job_id}", RequestJob
        )

    def stream_request_job_result(self, context_uuid: UUID, request_id: int, job_id: int) -> Stream:
        return self._stream(
            f"{self._context(context_uuid)}/requests/{request_id}/jobs/{job_id}/result"
        )

    # ------------------------------------------------------------------ reference data

    async def list_routines(
        self, *, query: str = "", vulnerability_id: int | None = None, page: int = 0, size: int = 30
    ) -> Page[RoutineSummary]:
        params = {"query": query, "vulnId": vulnerability_id, **page_params(page, size)}
        return await self._get_page("/api/routines", RoutineSummary, params)

    async def get_routine(self, routine_id: int) -> RoutineDetailed:
        return await self._get_model(f"/api/routines/{routine_id}", RoutineDetailed)

    async def list_vulnerabilities(
        self, *, query: str = "", parent_id: int | None = None, page: int = 0, size: int = 30
    ) -> Page[VulnerabilitySummary]:
        params = {"query": query, "parentVulnId": parent_id, **page_params(page, size)}
        return await self._get_page("/api/vulnerabilities", VulnerabilitySummary, params)

    async def get_vulnerability(self, vulnerability_id: int) -> VulnerabilityDetailed:
        return await self._get_model(
            f"/api/vulnerabilities/{vulnerability_id}", VulnerabilityDetailed
        )

    async def list_wordlists(
        self, *, query: str = "", vulnerability_id: int | None = None, page: int = 0, size: int = 30
    ) -> Page[WordlistSummary]:
        params = {"query": query, "vulnId": vulnerability_id, **page_params(page, size)}
        return await self._get_page("/api/wordlists", WordlistSummary, params)

    async def get_wordlist(self, wordlist_id: int) -> WordlistDetailed:
        return await self._get_model(f"/api/wordlists/{wordlist_id}", WordlistDetailed)

    async def create_wordlist(
        self, content: str | bytes, *, file_name: str, metadata: WordlistCreation | None = None
    ) -> int:
        """Upload a wordlist (multipart: ``file`` and optional ``metadata``); returns its id."""
        data = content.encode("utf-8") if isinstance(content, str) else content
        files: dict[str, tuple[str | None, bytes, str]] = {"file": (file_name, data, "text/plain")}
        if metadata is not None:
            payload = json.dumps(metadata.to_payload()).encode("utf-8")
            files["metadata"] = (None, payload, "application/json")
        response = await self._send("POST", "/api/wordlists", files=files)
        return id_from_location(response)

    # ------------------------------------------------------------------ findings

    async def create_finding(self, context_uuid: UUID, finding: AiFindingCreation) -> int:
        response = await self._send(
            "POST", f"{self._context(context_uuid)}/findings", json_body=finding.to_payload()
        )
        return id_from_location(response)

    async def get_finding(self, context_uuid: UUID, finding_id: int) -> AgentFindingDetailed:
        return await self._get_model(
            f"{self._context(context_uuid)}/findings/{finding_id}", AgentFindingDetailed
        )

    async def list_findings(
        self,
        context_uuid: UUID,
        *,
        request_id: int | None = None,
        job_id: int | None = None,
        status: FindingStatus | None = None,
        human_verdict: HumanVerdict | None = None,
        created_by: FindingCreator | None = None,
        page: int = 0,
        size: int = DEFAULT_PAGE_SIZE,
    ) -> Page[AgentFindingSummary]:
        params = {
            "requestId": request_id,
            "jobId": job_id,
            "status": None if status is None else str(status),
            "humanVerdict": None if human_verdict is None else str(human_verdict),
            "createdBy": None if created_by is None else str(created_by),
            **page_params(page, size),
        }
        return await self._get_page(
            f"{self._context(context_uuid)}/findings", AgentFindingSummary, params
        )

    async def update_finding(
        self, context_uuid: UUID, finding_id: int, patch: FindingPatch
    ) -> None:
        await self._send(
            "PATCH",
            f"{self._context(context_uuid)}/findings/{finding_id}",
            json_body=patch.to_payload(),
        )

    # ------------------------------------------------------------------ export and ingestion

    async def export_body_property_names(self, host_id: int) -> list[str]:
        """One name per line. Only ``hostId`` is ever sent (the plan forbids ``programId``)."""
        response = await self._send(
            "GET", "/api/export/body-properties", params={"hostId": host_id}
        )
        return [line for line in response.text.splitlines() if line.strip()]

    async def add_request(self, addition: AiRequestAddition) -> RequestSummary:
        data = await self._json(
            "POST", "/api/external/ai/requests", json_body=addition.to_payload()
        )
        try:
            return RequestSummary.model_validate(data)
        except ValidationError as exc:
            raise CoreResponseError(
                f"The core answered POST /api/external/ai/requests with an unexpected shape: {exc}"
            ) from exc
