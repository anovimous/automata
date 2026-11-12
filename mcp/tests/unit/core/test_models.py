from collections.abc import Callable
from typing import Any

import pytest
from pydantic import BaseModel, ValidationError

from automata_mcp.core.models.base import CoreRequest
from automata_mcp.core.models.context import (
    AgentContextDetailed,
    ContextRequestSummary,
    HostDetailed,
    ResolvedTarget,
)
from automata_mcp.core.models.enums import (
    BodyStorage,
    ConfigFileStatus,
    FailureCategory,
    FindingStatus,
    JobState,
    Origin,
)
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
from automata_mcp.core.models.tenants import AgentContextTenant, AuthNameValuePair, StaticData
from automata_mcp.core.paging import Page

SINGLE = [
    ("agent_context_detailed", AgentContextDetailed),
    ("host_detailed", HostDetailed),
    ("request_metadata", RequestMetadata),
    ("request_raw", RequestRaw),
    ("response_metadata", ResponseMetadata),
    ("response_raw", ResponseRaw),
    ("request_summary", RequestSummary),
    ("request_job", RequestJob),
    ("job_detailed", HttpJob),
    ("routine_detailed", RoutineDetailed),
    ("vulnerability_detailed", VulnerabilityDetailed),
    ("wordlist_detailed", WordlistDetailed),
    ("finding_detailed", AgentFindingDetailed),
]
LISTS = [
    ("resolved_targets", ResolvedTarget),
    ("context_tenants", AgentContextTenant),
]
PAGES = [
    ("context_requests_page", ContextRequestSummary),
    ("job_summary_page", HttpJobSummary),
    ("request_job_summary_page", RequestJobSummary),
    ("routine_summary_page", RoutineSummary),
    ("vulnerability_summary_page", VulnerabilitySummary),
    ("wordlist_summary_page", WordlistSummary),
    ("finding_summary_page", AgentFindingSummary),
]
REQUEST_BODIES = [
    ("ai_job_creation", AiJobCreation),
    ("ai_result", AiResult),
    ("ai_finding_creation", AiFindingCreation),
    ("finding_patch", FindingPatch),
    ("ai_request_addition", AiRequestAddition),
    ("wordlist_creation", WordlistCreation),
]


@pytest.mark.parametrize(("name", "model"), SINGLE)
def test_single_payloads_parse(
    name: str, model: type[BaseModel], load_fixture: Callable[[str], Any]
) -> None:
    assert model.model_validate(load_fixture(name))


@pytest.mark.parametrize(("name", "model"), LISTS)
def test_list_payloads_parse(
    name: str, model: type[BaseModel], load_fixture: Callable[[str], Any]
) -> None:
    items = [model.model_validate(item) for item in load_fixture(name)]

    assert items


@pytest.mark.parametrize(("name", "model"), PAGES)
def test_page_payloads_parse(
    name: str, model: type[BaseModel], load_fixture: Callable[[str], Any]
) -> None:
    page = Page[model].model_validate(load_fixture(name))  # type: ignore[valid-type]

    assert page.content
    assert page.last is True


@pytest.mark.parametrize(("name", "model"), REQUEST_BODIES)
def test_request_bodies_round_trip(
    name: str, model: type[CoreRequest], load_fixture: Callable[[str], Any]
) -> None:
    fixture = load_fixture(name)

    assert model.model_validate(fixture).to_payload() == fixture


def test_extra_fields_from_the_core_are_ignored(load_fixture: Callable[[str], Any]) -> None:
    data = load_fixture("host_detailed") | {"somethingNew": {"a": 1}}

    assert HostDetailed.model_validate(data).host == "api.example.com"


def test_models_accept_python_names_too() -> None:
    assert AiResult(ai_result="done").to_payload() == {"aiResult": "done"}


def test_context_detail_has_typed_items(load_fixture: Callable[[str], Any]) -> None:
    context = AgentContextDetailed.model_validate(load_fixture("agent_context_detailed"))

    assert context.uuid == "6f1c0a52-0000-4000-8000-000000000001"
    assert [item.item_type for item in context.items] == ["REQUEST", "EQUALITY_SET"]
    assert context.items[1].equality_set_id == 55
    assert context.default_duration == "SHORT"
    assert context.allowed_routine_ids == [1, 2]


def test_tenant_without_authentication_has_none(load_fixture: Callable[[str], Any]) -> None:
    with_auth, without_auth = (
        AgentContextTenant.model_validate(item) for item in load_fixture("context_tenants")
    )

    assert with_auth.auth_data is not None
    assert with_auth.auth_data.data is not None
    assert with_auth.auth_data.data.headers[0].key == "Authorization"
    assert with_auth.auth_data.data.query_parameters == []
    assert with_auth.request_ids == [7, 21]
    assert without_auth.auth_data is None
    assert without_auth.request_ids == []


def test_request_metadata_keeps_the_target_and_body(load_fixture: Callable[[str], Any]) -> None:
    metadata = RequestMetadata.model_validate(load_fixture("request_metadata"))

    assert metadata.line.target == "/api/users/42?expand=items"
    assert metadata.line.computated_path == "/api/users/{id}"
    assert metadata.body.storage is BodyStorage.INLINE
    assert metadata.body.mime_family == "JSON"
    assert metadata.headers[0].name == "Host"


def test_raw_response_of_a_file_body_has_no_inline_body(load_fixture: Callable[[str], Any]) -> None:
    raw = ResponseRaw.model_validate(load_fixture("response_raw"))

    assert raw.raw_body_base64 is None
    assert raw.body_stored_as_file is True


def test_job_flags_come_from_the_core(load_fixture: Callable[[str], Any]) -> None:
    job = HttpJob.model_validate(load_fixture("job_detailed"))

    assert job.current_state is JobState.DRAFT
    assert job.waiting_for_action is True
    assert job.terminal is False
    assert job.config_file_status is ConfigFileStatus.READY
    assert job.routine is not None
    assert job.routine.key == "fuzz"
    assert job.generic_config is not None
    assert job.generic_config.match_and_replace[0].rule == "REPLACE"
    assert job.target_selector is not None
    assert job.target_selector.selector == {"requestId": 7}


def test_request_job_carries_failure_category_and_origin(
    load_fixture: Callable[[str], Any],
) -> None:
    job = RequestJob.model_validate(load_fixture("request_job"))

    assert job.failure_category is FailureCategory.TOOL_EXIT
    assert job.origin is Origin.THIS_CONTEXT
    assert job.custom_config == {"flags": [{"key": "-mc", "value": "200"}]}


def test_unknown_enum_values_do_not_break_a_payload(load_fixture: Callable[[str], Any]) -> None:
    data = load_fixture("job_detailed") | {
        "currentState": "ARCHIVED",
        "failureCategory": "COSMIC_RAY",
    }

    job = HttpJob.model_validate(data)

    assert job.current_state == "ARCHIVED"
    assert job.failure_category == "COSMIC_RAY"


def test_routine_detail_keeps_the_config_schema(load_fixture: Callable[[str], Any]) -> None:
    routine = RoutineDetailed.model_validate(load_fixture("routine_detailed"))

    assert routine.config_schema["properties"]["rawRequestBase64"] == {"type": "string"}
    assert routine.wordlist_required is True
    assert routine.is_available_at_consumer is True


def test_routine_detail_has_the_usage_description_for_agent(
    load_fixture: Callable[[str], Any],
) -> None:
    routine = RoutineDetailed.model_validate(load_fixture("routine_detailed"))

    assert routine.description == "ffuf based fuzzer"
    assert routine.usage_description_for_agent.startswith("Fuzz one request")
    assert routine.to_output()["usageDescriptionForAgent"] == routine.usage_description_for_agent


def test_routine_summary_has_no_usage_description_for_agent() -> None:
    assert "usage_description_for_agent" not in RoutineSummary.model_fields
    assert "description" not in RoutineSummary.model_fields


def test_finding_detail_has_the_human_verdict(load_fixture: Callable[[str], Any]) -> None:
    finding = AgentFindingDetailed.model_validate(load_fixture("finding_detailed"))

    assert finding.human_verdict == "TRUE_POSITIVE"
    assert finding.status is FindingStatus.CONFIRMED
    assert finding.severity == 7.5


def test_job_creation_sends_wordlists_ids_even_when_empty() -> None:
    payload = AiJobCreation(
        request_id=7, ai_description="d", routine_id=1, wordlists_ids=[]
    ).to_payload()

    assert payload == {"requestId": 7, "aiDescription": "d", "routineId": 1, "wordlistsIds": []}


def test_job_creation_serializes_enums_as_strings() -> None:
    payload = AiJobCreation.model_validate(
        {
            "requestId": 1,
            "aiDescription": "d",
            "routineId": 1,
            "wordlistsIds": [],
            "duration": "LONG",
            "verbosity": "VERBOSE",
        }
    ).to_payload()

    assert payload["duration"] == "LONG"
    assert type(payload["duration"]) is str
    assert type(payload["verbosity"]) is str


def test_patch_sends_only_the_given_fields() -> None:
    assert FindingPatch().to_payload() == {}
    assert FindingPatch(severity=3.0).to_payload() == {"severity": 3.0}


def test_patch_flags_to_clear_a_field_are_sent_only_when_set() -> None:
    payload = FindingPatch(set_poc_reproduction_steps_null=True).to_payload()

    assert payload == {"setPocReproductionStepsNull": True}


def test_patch_keeps_a_status_given_as_an_enum() -> None:
    assert FindingPatch(status=FindingStatus.CONFIRMED).to_payload() == {"status": "CONFIRMED"}


def test_request_addition_leaves_out_what_was_not_given() -> None:
    payload = AiRequestAddition(request_base64="QQ==", host="api.example.com").to_payload()

    assert payload == {"requestBase64": "QQ==", "host": "api.example.com"}


def test_wordlist_creation_needs_only_the_descriptions() -> None:
    payload = WordlistCreation(small_description="s", payload_content_description="p").to_payload()

    assert payload == {"smallDescription": "s", "payloadContentDescription": "p"}


@pytest.mark.parametrize("missing", ["small_description", "payload_content_description"])
def test_wordlist_creation_without_a_description_is_refused(missing: str) -> None:
    arguments = {"small_description": "s", "payload_content_description": "p"}
    del arguments[missing]

    with pytest.raises(ValidationError):
        WordlistCreation(**arguments)


def test_default_lists_of_a_model_are_not_shared() -> None:
    first, second = StaticData(), StaticData()
    first.headers.append(AuthNameValuePair(key="k", value="v"))

    assert second.headers == []


def test_to_output_drops_null_fields_but_keeps_null_inside_free_form_json() -> None:
    routine = RoutineDetailed.model_validate(
        {
            "id": 1,
            "key": "fuzz",
            "description": "d",
            "usageDescriptionForAgent": "u",
            "vulnerabilityId": None,
            "configSchema": {"properties": {"a": {"default": None}}},
        }
    )

    output = routine.to_output()

    assert "vulnerabilityId" not in output
    assert output["isAvailableAtConsumer"] is False
    assert output["configSchema"] == {"properties": {"a": {"default": None}}}


def test_to_output_gives_plain_strings_for_enums() -> None:
    target = ResolvedTarget.model_validate(
        {
            "requestId": 1,
            "automataJobEligible": True,
            "autoApproval": False,
            "sourceItemType": "REQUEST",
        }
    )

    assert type(target.to_output()["sourceItemType"]) is str
