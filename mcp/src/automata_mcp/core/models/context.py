from automata_mcp.core.models.base import CoreModel
from automata_mcp.core.models.enums import (
    AgentContextItemType,
    Duration,
    HostScope,
    Method,
    RepresentativeStrategy,
    ResultsVerbosity,
)


class AgentContextItem(CoreModel):
    id: int
    item_type: AgentContextItemType
    request_id: int | None = None
    equality_set_id: int | None = None


class AgentContextDetailed(CoreModel):
    id: int
    uuid: str
    name: str
    description: str | None = None
    host_id: int
    default_auto_approval: bool = False
    representative_strategy: RepresentativeStrategy
    allowed_routine_ids: list[int] = []
    tenant_ids: list[int] = []
    default_duration: Duration | None = None
    default_verbosity: ResultsVerbosity | None = None
    default_priority: int | None = None
    max_rate: int | None = None
    creation_date: str | None = None
    items: list[AgentContextItem] = []


class ResolvedTarget(CoreModel):
    request_id: int
    automata_job_eligible: bool
    auto_approval: bool
    source_item_type: AgentContextItemType
    source_equality_set_id: int | None = None


class HostDetailed(CoreModel):
    id: int
    insertion_date: str | None = None
    host: str
    level: int | None = None
    scope: HostScope | None = None
    out_of_scope: bool = False
    host_rate_limit: int
    short_rate_limit: int
    long_rate_limit: int
    program_id: int | None = None
    description: str | None = None
    technologies: list[str] | None = None
    notes: str | None = None


class ContextRequestSummary(CoreModel):
    request_id: int
    method: Method
    computated_path: str
    equality_set_id: int | None = None
    tenant_id: int | None = None
    resolved_target: bool
    automata_job_eligible: bool
    insertion_date: str | None = None
