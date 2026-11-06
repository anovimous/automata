"""scripts/check_core_contract.py: the routes the client uses against an OpenAPI document."""

import asyncio
import json
from pathlib import Path
from typing import Any

import httpx
import pytest
import respx

import check_core_contract as contract
from automata_mcp.core.client import CoreClient

BASE = "http://core.test:8080"
CONTEXT = str(contract.CONTEXT)


def templated(path: str) -> str:
    """The OpenAPI template of a recorded path: the uuid and the sample id become parameters."""
    parts = [
        "{uuid}" if part == CONTEXT else "{id}" if part == str(contract.ID) else part
        for part in path.split("/")
    ]
    return "/".join(parts)


def openapi_for(calls: list[contract.Call], *, pageable: bool = False) -> dict[str, Any]:
    """A document that describes exactly the given calls."""
    paths: dict[str, Any] = {}
    for call in calls:
        names = [name for name in call.query if not (pageable and name in {"page", "size"})]
        parameters = [{"name": n, "in": "query"} for n in names]
        if pageable and {"page", "size"} & set(call.query):
            parameters.append({"name": "pageable", "in": "query"})
        paths.setdefault(templated(call.path), {})[call.method.lower()] = {"parameters": parameters}
    return {"openapi": "3.1.0", "paths": paths}


@pytest.fixture(scope="module")
def calls() -> list[contract.Call]:
    return asyncio.run(contract.record_calls())


# ---------------------------------------------------------------------------- the recording


def test_every_public_method_of_the_client_has_a_sample_call() -> None:
    assert contract.uncovered_methods() == set()


def test_a_new_method_of_the_client_is_reported_as_uncovered() -> None:
    class Extended(CoreClient):
        async def list_everything(self) -> None: ...

        async def _private_helper(self) -> None: ...

    assert contract.uncovered_methods(Extended) == {"list_everything"}


def test_public_methods_leaves_out_private_ones_and_aclose() -> None:
    names = contract.public_methods(CoreClient)

    assert "get_job" in names
    assert "stream_job_result" in names
    assert "aclose" not in names
    assert not [n for n in names if n.startswith("_")]


def test_one_call_is_recorded_per_method(calls: list[contract.Call]) -> None:
    assert [c.client_method for c in calls] == list(contract.SAMPLE_CALLS)
    assert len(calls) == 35


def test_the_recorded_requests_are_the_ones_the_client_sends(calls: list[contract.Call]) -> None:
    by_name = {c.client_method: c for c in calls}

    assert by_name["create_wordlist"] == contract.Call(
        "create_wordlist", "POST", "/api/wordlists", ()
    )
    assert by_name["add_request"].path == "/api/external/ai/requests"
    assert by_name["export_body_property_names"].query == ("hostId",)
    assert by_name["list_jobs"].query == ("page", "size", "states")
    assert by_name["list_findings"].query == (
        "createdBy",
        "humanVerdict",
        "jobId",
        "page",
        "requestId",
        "size",
        "status",
    )
    assert by_name["update_finding"].method == "PATCH"
    assert by_name["queue_job"].path == f"/api/agent-contexts/uuid/{CONTEXT}/jobs/7/queue"
    assert by_name["stream_request_job_result"].path.endswith("/requests/7/jobs/7/result")


def test_a_sample_that_makes_no_request_is_an_error() -> None:
    async def nothing(_: CoreClient) -> None:
        return None

    with pytest.raises(RuntimeError, match="made no request"):
        asyncio.run(contract.record_calls({"nothing": nothing}))


# ---------------------------------------------------------------------------- matching


@pytest.mark.parametrize(
    ("template", "concrete", "expected"),
    [
        ("/api/jobs/{id}", "/api/jobs/7", True),
        ("/api/jobs/{id}", "/api/jobs/7/", True),
        ("/api/jobs/{id}", "/api/jobs/7/result", False),
        ("/api/jobs/{id}/result", "/api/jobs/7", False),
        ("/api/jobs/poll", "/api/jobs/7", False),
        ("/api/jobs/poll", "/api/jobs/poll", True),
        ("/api/{a}/{b}", "/api/x/y", True),
        ("/api/{a}/{b}", "/other/x/y", False),
    ],
)
def test_matches(template: str, concrete: str, expected: bool) -> None:
    assert contract.matches(template, concrete) is expected


def test_the_most_literal_template_wins() -> None:
    paths: dict[str, Any] = {"/api/jobs/{id}": {}, "/api/jobs/poll": {}}

    assert contract.find_path(paths, "/api/jobs/poll") == "/api/jobs/poll"
    assert contract.find_path(paths, "/api/jobs/7") == "/api/jobs/{id}"
    assert contract.find_path(paths, "/api/nothing") is None


def test_declared_query_parameters_come_from_the_operation_and_the_path_item() -> None:
    path_item = {"parameters": [{"name": "shared", "in": "query"}, {"name": "id", "in": "path"}]}
    operation = {
        "parameters": [{"name": "states", "in": "query"}, {"name": "X-Key", "in": "header"}]
    }

    assert contract.declared_query_parameters(path_item, operation) == {"shared", "states"}


def test_a_pageable_parameter_stands_for_page_size_and_sort() -> None:
    operation = {"parameters": [{"name": "pageable", "in": "query"}]}

    assert contract.declared_query_parameters({}, operation) == {"page", "size", "sort"}


# ---------------------------------------------------------------------------- check


def test_a_document_that_describes_the_calls_has_no_problem(calls: list[contract.Call]) -> None:
    assert contract.check(calls, openapi_for(calls)) == []


def test_pageable_parameters_satisfy_page_and_size(calls: list[contract.Call]) -> None:
    assert contract.check(calls, openapi_for(calls, pageable=True)) == []


def test_a_missing_route_is_reported_with_the_method_that_needs_it(
    calls: list[contract.Call],
) -> None:
    document = openapi_for(calls)
    del document["paths"]["/api/export/body-properties"]

    problems = contract.check(calls, document)

    assert problems == [
        "export_body_property_names: no route GET /api/export/body-properties in the core"
    ]


def test_a_route_with_another_method_is_reported(calls: list[contract.Call]) -> None:
    document = openapi_for(calls)
    document["paths"]["/api/wordlists"].pop("post")

    problems = contract.check(calls, document)

    assert len(problems) == 1
    assert problems[0].startswith("create_wordlist: /api/wordlists exists but not for POST")
    assert "['GET']" in problems[0]


def test_a_query_parameter_the_core_does_not_declare_is_reported(
    calls: list[contract.Call],
) -> None:
    document = openapi_for(calls)
    operation = document["paths"]["/api/agent-contexts/uuid/{uuid}/jobs"]["get"]
    operation["parameters"] = [p for p in operation["parameters"] if p["name"] != "states"]

    problems = contract.check(calls, document)

    assert len(problems) == 1
    assert "list_jobs" in problems[0]
    assert "no query parameter 'states'" in problems[0]


def test_every_problem_is_listed_not_only_the_first(calls: list[contract.Call]) -> None:
    document = openapi_for(calls)
    del document["paths"]["/api/routines"]
    del document["paths"]["/api/wordlists/{id}"]

    assert len(contract.check(calls, document)) == 2


def test_an_empty_document_misses_every_route(calls: list[contract.Call]) -> None:
    assert len(contract.check(calls, {"paths": {}})) == len(calls)


# ---------------------------------------------------------------------------- loading


def test_the_document_is_read_from_a_file(tmp_path: Path) -> None:
    file = tmp_path / "openapi.json"
    file.write_text(json.dumps({"paths": {"/a": {}}}))

    assert contract.load_openapi(None, file) == {"paths": {"/a": {}}}


@respx.mock
def test_the_document_is_read_from_the_default_path_of_springdoc() -> None:
    route = respx.get(f"{BASE}/v3/api-docs").respond(200, json={"paths": {}})

    assert contract.load_openapi(f"{BASE}/", None) == {"paths": {}}
    assert route.call_count == 1


@respx.mock
def test_the_core_url_defaults_to_localhost() -> None:
    route = respx.get("http://localhost:8080/v3/api-docs").respond(200, json={"paths": {}})

    contract.load_openapi(None, None)

    assert route.call_count == 1


@respx.mock
def test_a_failing_core_is_an_http_error() -> None:
    respx.get(f"{BASE}/v3/api-docs").respond(500)

    with pytest.raises(httpx.HTTPError):
        contract.load_openapi(BASE, None)


def test_something_that_is_not_openapi_is_refused(tmp_path: Path) -> None:
    file = tmp_path / "other.json"
    file.write_text(json.dumps({"hello": "world"}))

    with pytest.raises(ValueError, match="not an OpenAPI document"):
        contract.load_openapi(None, file)


# ---------------------------------------------------------------------------- main


def write_document(tmp_path: Path, document: dict[str, Any]) -> Path:
    file = tmp_path / "openapi.json"
    file.write_text(json.dumps(document))
    return file


def test_main_succeeds_when_every_route_exists(
    tmp_path: Path, calls: list[contract.Call], capsys: pytest.CaptureFixture[str]
) -> None:
    file = write_document(tmp_path, openapi_for(calls))

    assert contract.main(["--file", str(file)]) == 0
    assert "OK: the 35 calls of the client exist in the core." in capsys.readouterr().out


def test_main_exits_with_1_and_lists_what_is_missing(
    tmp_path: Path, calls: list[contract.Call], capsys: pytest.CaptureFixture[str]
) -> None:
    document = openapi_for(calls)
    del document["paths"]["/api/routines"]
    file = write_document(tmp_path, document)

    assert contract.main(["--file", str(file)]) == 1
    output = capsys.readouterr().out
    assert "list_routines: no route GET /api/routines in the core" in output
    assert "1 problem(s) in 35 calls." in output


def test_main_exits_with_2_when_the_document_cannot_be_read(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    assert contract.main(["--file", str(tmp_path / "missing.json")]) == 2
    assert "Cannot read the OpenAPI document" in capsys.readouterr().err


@respx.mock
def test_main_exits_with_2_when_the_core_is_down(capsys: pytest.CaptureFixture[str]) -> None:
    respx.get(f"{BASE}/v3/api-docs").mock(side_effect=httpx.ConnectError("refused"))

    assert contract.main(["--core-url", BASE]) == 2
    assert "Cannot read the OpenAPI document" in capsys.readouterr().err


@respx.mock
def test_main_reads_the_core_from_the_environment(
    monkeypatch: pytest.MonkeyPatch, calls: list[contract.Call]
) -> None:
    monkeypatch.setenv("AUTOMATA_CORE_URL", BASE)
    route = respx.get(f"{BASE}/v3/api-docs").respond(200, json=openapi_for(calls))

    assert contract.main([]) == 0
    assert route.call_count == 1


def test_main_refuses_to_run_when_a_method_has_no_sample_call(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setattr(contract, "uncovered_methods", lambda: {"list_everything"})

    assert contract.main(["--file", "unused.json"]) == 1
    assert "Add a sample call for: list_everything" in capsys.readouterr().err
