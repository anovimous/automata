from automata_mcp.core.models.base import CoreModel, CoreRequest
from automata_mcp.core.models.enums import FindingCreator, FindingStatus, HumanVerdict, Origin


class AgentFindingSummary(CoreModel):
    id: int
    request_id: int
    host_id: int | None = None
    vulnerability_id: int | None = None
    severity: float
    status: FindingStatus
    confidence: float
    created_by: FindingCreator
    origin: Origin
    human_verdict: HumanVerdict | None = None
    creation_date: str | None = None


class AgentFindingDetailed(CoreModel):
    id: int
    creation_date: str | None = None
    request_id: int
    host_id: int | None = None
    job_id: int | None = None
    vulnerability_id: int | None = None
    severity: float
    status: FindingStatus
    confidence: float
    evidence: str
    poc_reproduction_steps: str | None = None
    created_by: FindingCreator
    origin: Origin
    ai_description: str | None = None
    human_verdict: HumanVerdict | None = None
    human_comment: str | None = None
    reviewed_at: str | None = None


class AiFindingCreation(CoreRequest):
    """Body of ``POST .../findings``; the core sets the creator and the context."""

    request_id: int
    job_id: int | None = None
    vulnerability_id: int
    severity: float
    status: FindingStatus
    confidence: float
    evidence: str
    poc_reproduction_steps: str | None = None
    ai_description: str | None = None


class FindingPatch(CoreRequest):
    """Body of ``PATCH .../findings/{id}``: only the fields to change are sent, never a null.

    To clear a text field, set the matching ``set_..._null`` flag to True.
    """

    severity: float | None = None
    status: FindingStatus | None = None
    confidence: float | None = None
    evidence: str | None = None
    poc_reproduction_steps: str | None = None
    set_poc_reproduction_steps_null: bool | None = None
    ai_description: str | None = None
    set_ai_description_null: bool | None = None
