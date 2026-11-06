"""Check that the routes this server calls exist in the core.

Every method of ``CoreClient`` is called once against a transport that records the request and
stops, so the method, the path and the query parameters are the ones the client really sends, not
a copy. They are then compared with the OpenAPI document of the core (springdoc, by default
``<core>/v3/api-docs``): the route must exist with that method, and every query parameter the
client sends must be one the route declares.

    uv run python scripts/check_core_contract.py                    # the core at AUTOMATA_CORE_URL
    uv run python scripts/check_core_contract.py --core-url http://core:8080
    uv run python scripts/check_core_contract.py --file openapi.json     # a saved document

Exit status: 0 everything exists, 1 something is missing, 2 the document could not be read.
"""

import argparse
import asyncio
import inspect
import json
import os
import sys
from collections.abc import Awaitable, Callable, Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from uuid import UUID

import httpx

from automata_mcp.core.client import CoreClient
from automata_mcp.core.models.enums import FindingCreator, FindingStatus, HumanVerdict, JobState
from automata_mcp.core.models.findings import AiFindingCreation, FindingPatch
from automata_mcp.core.models.jobs import AiJobCreation
from automata_mcp.core.models.reference import WordlistCreation
from automata_mcp.core.models.requests import AiRequestAddition

DEFAULT_CORE_URL = "http://localhost:8080"
OPENAPI_PATH = "/v3/api-docs"
PAGEABLE_PARAMETERS = frozenset(
    {"page", "size", "sort"}
)  # springdoc lists a Pageable as one parameter
HTTP_METHODS = frozenset({"get", "post", "put", "patch", "delete", "head", "options"})

CONTEXT = UUID("6f1c0a52-0000-4000-8000-000000000001")
ID = 7

type Invoke = Callable[[CoreClient], Awaitable[object]]


async def _enter(stream: Any) -> None:
    async with stream:
        pass


# One call per public method of the client, with every optional parameter given so that every
# query parameter the client can send is recorded.
SAMPLE_CALLS: dict[str, Invoke] = {
    "get_context": lambda c: c.get_context(CONTEXT),
    "list_resolved_targets": lambda c: c.list_resolved_targets(CONTEXT),
    "get_host": lambda c: c.get_host(CONTEXT),
    "list_tenants": lambda c: c.list_tenants(CONTEXT),
    "list_context_requests": lambda c: c.list_context_requests(CONTEXT, equality_set_id=ID),
    "get_request_metadata": lambda c: c.get_request_metadata(CONTEXT, ID),
    "get_request_raw": lambda c: c.get_request_raw(CONTEXT, ID),
    "stream_request_body": lambda c: _enter(c.stream_request_body(CONTEXT, ID)),
    "get_response_metadata": lambda c: c.get_response_metadata(CONTEXT, ID),
    "get_response_raw": lambda c: c.get_response_raw(CONTEXT, ID),
    "stream_response_body": lambda c: _enter(c.stream_response_body(CONTEXT, ID)),
    "create_job": lambda c: c.create_job(
        CONTEXT,
        AiJobCreation(request_id=ID, ai_description="d", routine_id=ID, wordlists_ids=[ID]),
    ),
    "list_jobs": lambda c: c.list_jobs(CONTEXT, states=[JobState.FINISHED]),
    "get_job": lambda c: c.get_job(CONTEXT, ID),
    "stream_job_result": lambda c: _enter(c.stream_job_result(CONTEXT, ID)),
    "queue_job": lambda c: c.queue_job(CONTEXT, ID),
    "retry_job_preparation": lambda c: c.retry_job_preparation(CONTEXT, ID),
    "set_job_ai_result": lambda c: c.set_job_ai_result(CONTEXT, ID, "result"),
    "cancel_job": lambda c: c.cancel_job(CONTEXT, ID),
    "list_request_jobs": lambda c: c.list_request_jobs(CONTEXT, ID, states=[JobState.FINISHED]),
    "get_request_job": lambda c: c.get_request_job(CONTEXT, ID, ID),
    "stream_request_job_result": lambda c: _enter(c.stream_request_job_result(CONTEXT, ID, ID)),
    "list_routines": lambda c: c.list_routines(query="q", vulnerability_id=ID),
    "get_routine": lambda c: c.get_routine(ID),
    "list_vulnerabilities": lambda c: c.list_vulnerabilities(query="q", parent_id=ID),
    "get_vulnerability": lambda c: c.get_vulnerability(ID),
    "list_wordlists": lambda c: c.list_wordlists(query="q", vulnerability_id=ID),
    "get_wordlist": lambda c: c.get_wordlist(ID),
    "create_wordlist": lambda c: c.create_wordlist(
        "a\nb\n", file_name="w.txt", metadata=WordlistCreation(name="w")
    ),
    "create_finding": lambda c: c.create_finding(
        CONTEXT,
        AiFindingCreation(
            request_id=ID,
            vulnerability_id=ID,
            severity=1.0,
            status=FindingStatus.SUGGESTED,
            confidence=1.0,
            evidence="e",
        ),
    ),
    "get_finding": lambda c: c.get_finding(CONTEXT, ID),
    "list_findings": lambda c: c.list_findings(
        CONTEXT,
        request_id=ID,
        job_id=ID,
        status=FindingStatus.CONFIRMED,
        human_verdict=HumanVerdict.TRUE_POSITIVE,
        created_by=FindingCreator.AI,
    ),
    "update_finding": lambda c: c.update_finding(CONTEXT, ID, FindingPatch(severity=1.0)),
    "export_body_property_names": lambda c: c.export_body_property_names(ID),
    "add_request": lambda c: c.add_request(AiRequestAddition(request_base64="QQ==", host="h")),
}
NOT_ROUTES = frozenset({"aclose"})


class Recorded(Exception):
    """Raised by the recording transport to stop a call once its request is known."""

    def __init__(self, request: httpx.Request) -> None:
        super().__init__(f"{request.method} {request.url.path}")
        self.request = request


@dataclass(frozen=True)
class Call:
    """A request the client makes: which method makes it and what it sends."""

    client_method: str
    method: str
    path: str
    query: tuple[str, ...]


def public_methods(client_class: type) -> set[str]:
    """The names of the public methods of the client (the ones that may call the core)."""
    return {
        name
        for name, member in inspect.getmembers(client_class, predicate=inspect.isfunction)
        if not name.startswith("_") and name not in NOT_ROUTES
    }


def uncovered_methods(client_class: type = CoreClient) -> set[str]:
    """Methods of the client that have no sample call here (so their route would not be checked)."""
    return public_methods(client_class) - set(SAMPLE_CALLS)


async def record_calls(samples: dict[str, Invoke] | None = None) -> list[Call]:
    """Call every sample once and return the request each one made."""

    def handler(request: httpx.Request) -> httpx.Response:
        raise Recorded(request)

    calls: list[Call] = []
    async with CoreClient("http://core.invalid", transport=httpx.MockTransport(handler)) as client:
        for name, invoke in (SAMPLE_CALLS if samples is None else samples).items():
            try:
                await invoke(client)
            except Recorded as recorded:
                request = recorded.request
                query = tuple(sorted(set(request.url.params.keys())))
                calls.append(Call(name, request.method, request.url.path, query))
            else:
                raise RuntimeError(f"{name} made no request to the core")
    return calls


def matches(template: str, concrete: str) -> bool:
    """Does ``/api/x/{id}`` describe ``/api/x/7``? A ``{name}`` segment matches any one segment."""
    wanted, actual = template.strip("/").split("/"), concrete.strip("/").split("/")
    return len(wanted) == len(actual) and all(
        w == a or (w.startswith("{") and w.endswith("}"))
        for w, a in zip(wanted, actual, strict=True)
    )


def find_path(paths: dict[str, Any], concrete: str) -> str | None:
    """The template of the document that describes this path (the most literal one wins)."""
    candidates = [template for template in paths if matches(template, concrete)]
    if not candidates:
        return None
    return max(
        candidates, key=lambda template: sum(not s.startswith("{") for s in template.split("/"))
    )


def declared_query_parameters(path_item: dict[str, Any], operation: dict[str, Any]) -> set[str]:
    """The query parameters of an operation (its own and those of its path item)."""
    names: set[str] = set()
    for parameter in [*path_item.get("parameters", []), *operation.get("parameters", [])]:
        if parameter.get("in") == "query":
            name = parameter.get("name", "")
            names |= PAGEABLE_PARAMETERS if name == "pageable" else {name}
    return names


def check(calls: Sequence[Call], openapi: dict[str, Any]) -> list[str]:
    """What the core does not have: a line for each missing route or unknown query parameter."""
    paths: dict[str, Any] = openapi.get("paths", {})
    problems: list[str] = []
    for call in calls:
        template = find_path(paths, call.path)
        if template is None:
            problems.append(f"{call.client_method}: no route {call.method} {call.path} in the core")
            continue
        path_item = paths[template]
        operation = (
            path_item.get(call.method.lower()) if call.method.lower() in HTTP_METHODS else None
        )
        if operation is None:
            known = sorted(m.upper() for m in path_item if m in HTTP_METHODS)
            problems.append(
                f"{call.client_method}: {template} exists but not for {call.method} "
                f"(it has {known})"
            )
            continue
        declared = declared_query_parameters(path_item, operation)
        for name in call.query:
            if name not in declared:
                problems.append(
                    f"{call.client_method}: {call.method} {template} has no query parameter "
                    f"'{name}' (it has {sorted(declared)})"
                )
    return problems


def load_openapi(core_url: str | None, file: Path | None) -> dict[str, Any]:
    """The OpenAPI document of the core, from a saved file or from ``<core>/v3/api-docs``."""
    if file is not None:
        document = json.loads(file.read_text(encoding="utf-8"))
    else:
        url = f"{(core_url or DEFAULT_CORE_URL).rstrip('/')}{OPENAPI_PATH}"
        response = httpx.get(url, timeout=30)
        response.raise_for_status()
        document = response.json()
    if not isinstance(document, dict) or "paths" not in document:
        raise ValueError("this is not an OpenAPI document (it has no 'paths')")
    return document


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Check the routes of CoreClient against the core.")
    parser.add_argument("--core-url", default=os.environ.get("AUTOMATA_CORE_URL"))
    parser.add_argument("--file", type=Path, help="a saved OpenAPI document instead of the core")
    args = parser.parse_args(argv)

    missing = uncovered_methods()
    if missing:
        print(f"Add a sample call for: {', '.join(sorted(missing))}", file=sys.stderr)
        return 1
    try:
        openapi = load_openapi(args.core_url, args.file)
    except (httpx.HTTPError, OSError, ValueError) as error:
        print(f"Cannot read the OpenAPI document: {error}", file=sys.stderr)
        return 2

    calls = asyncio.run(record_calls())
    problems = check(calls, openapi)
    for problem in problems:
        print(problem)
    if problems:
        print(f"{len(problems)} problem(s) in {len(calls)} calls.")
        return 1
    print(f"OK: the {len(calls)} calls of the client exist in the core.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
