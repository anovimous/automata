from typing import Any

from automata_mcp.core.models.base import CoreModel, CoreRequest
from automata_mcp.core.models.enums import (
    ResultContentType,
    TargetCardinalityPair,
)


class RoutineSummary(CoreModel):
    id: int
    creation_date: str | None = None
    key: str
    is_available_at_consumer: bool = False
    wordlist_required: bool = False
    allowed_targets: list[TargetCardinalityPair] = []


class RoutineDetailed(CoreModel):
    """``GET /api/routines/{id}``: the only route that carries ``usageDescriptionForAgent``."""

    id: int
    creation_date: str | None = None
    key: str
    description: str
    usage_description_for_agent: str
    updated_at: str | None = None
    is_available_at_consumer: bool = False
    wordlist_required: bool = False
    allowed_targets: list[TargetCardinalityPair] = []
    vulnerability_id: int | None = None
    config_schema: Any = None
    result_content_type: ResultContentType | None = None


class VulnerabilitySummary(CoreModel):
    id: int
    name: str


class VulnerabilityDetailed(CoreModel):
    id: int
    name: str
    parent_id: int | None = None


class WordlistSummary(CoreModel):
    id: int
    name: str | None = None
    description: str | None = None
    number_of_lines: int | None = None
    size_bytes: int | None = None


class WordlistDetailed(CoreModel):
    id: int
    name: str | None = None
    description: str | None = None
    number_of_lines: int | None = None
    size_bytes: int | None = None
    sha256: str | None = None


class WordlistCreation(CoreRequest):
    """The ``metadata`` part of ``POST /api/wordlists``; ``name`` falls back to the file name."""

    name: str | None = None
    description: str | None = None
    vuln_ids: list[int] | None = None
