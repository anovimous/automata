from typing import Any

from automata_mcp.core.models.base import CoreModel, CoreRequest
from automata_mcp.core.models.enums import (
    ConfigFileStatus,
    Duration,
    FailureCategory,
    HttpJobScope,
    JobCreator,
    JobState,
    Origin,
    ResultsVerbosity,
    SelectorType,
)


class HttpJobSummary(CoreModel):
    """A job of the context, as listed by ``list_jobs``."""

    id: int
    creation_date: str | None = None
    scope: HttpJobScope | None = None
    current_state: JobState
    priority: int | None = None
    rate: int | None = None
    routine_id: int | None = None
    routine_key: str | None = None
    created_by: JobCreator | None = None
    approved: bool = False
    terminal: bool = False
    waiting_for_action: bool = False


class RequestJobSummary(CoreModel):
    """A job of the history of a request, as listed by ``list_request_jobs``."""

    id: int
    creation_date: str | None = None
    scope: HttpJobScope | None = None
    current_state: JobState
    priority: int | None = None
    rate: int | None = None
    routine_key: str | None = None
    approved: bool = False
    terminal: bool = False
    waiting_for_action: bool = False
    origin: Origin


class RequestJob(CoreModel):
    """A job of the history of a request, in full (no context id, program or target selector)."""

    job_id: int
    current_state: JobState
    requested_state: JobState | None = None
    verbosity: ResultsVerbosity | None = None
    priority: int | None = None
    rate: int | None = None
    custom_config: Any = None
    routine_key: str | None = None
    failure_message: str | None = None
    failure_category: FailureCategory | None = None
    ai_description: str | None = None
    ai_result: str | None = None
    approved: bool = False
    terminal: bool = False
    waiting_for_action: bool = False
    origin: Origin


class TargetSelector(CoreModel):
    selector_type: SelectorType | None = None
    selector: Any = None


class MatchAndReplace(CoreModel):
    target_type: str | None = None
    rule: str | None = None
    target_key: str | None = None
    value: str | None = None


class GenericConfig(CoreModel):
    match_and_replace: list[MatchAndReplace] = []


class ProgramRef(CoreModel):
    id: int
    name: str | None = None


class RoutineRef(CoreModel):
    id: int
    key: str


class HttpJob(CoreModel):
    """A job of the context, in full."""

    job_id: int
    http_job_scope: HttpJobScope | None = None
    current_state: JobState
    requested_state: JobState | None = None
    verbosity: ResultsVerbosity | None = None
    priority: int | None = None
    rate: int | None = None
    target_selector: TargetSelector | None = None
    generic_config: GenericConfig | None = None
    custom_config: Any = None
    program: ProgramRef | None = None
    routine: RoutineRef | None = None
    failure_message: str | None = None
    failure_category: FailureCategory | None = None
    config_file_status: ConfigFileStatus | None = None
    config_file_failure: str | None = None
    ai_description: str | None = None
    ai_result: str | None = None
    created_by: JobCreator | None = None
    approved: bool = False
    terminal: bool = False
    waiting_for_action: bool = False
    agent_context_uuid: str | None = None


class AiJobCreation(CoreRequest):
    """Body of ``POST .../jobs``; the context uuid is in the path. Note ``wordlistsIds``."""

    request_id: int
    ai_description: str
    routine_id: int
    duration: Duration | None = None
    wordlists_ids: list[int]
    rate: int | None = None
    verbosity: ResultsVerbosity | None = None
    priority: int | None = None
    custom_config: dict[str, Any] | None = None


class AiResult(CoreRequest):
    """Body of ``POST .../jobs/{id}/ai-result``."""

    ai_result: str
