"""What the finding tools do around one call to the core.

The core scopes the findings to the context and enforces every rule (what may be edited, what a
confirmed finding needs, the ranges). These functions only build the body and report the outcome.
"""

from typing import Any
from uuid import UUID

from mcp.server.mcpserver.exceptions import ToolError

from automata_mcp.core.client import CoreClient
from automata_mcp.core.errors import CoreError
from automata_mcp.core.models.findings import AiFindingCreation, FindingPatch

REVIEW_NOTE = (
    "A human will review this finding; their verdict (TRUE_POSITIVE or FALSE_POSITIVE) and "
    "comment will appear in get_finding."
)


async def create_finding(
    core: CoreClient,
    context_uuid: UUID,
    *,
    request_id: int,
    vulnerability_id: int,
    severity: float,
    confidence: float,
    status: str,
    evidence: str,
    poc_reproduction_steps: str | None,
    job_id: int | None,
    ai_description: str | None,
) -> dict[str, Any]:
    """Create the finding; the core sets the creator and the context and checks every field."""
    creation = AiFindingCreation.model_validate(
        {
            "requestId": request_id,
            "jobId": job_id,
            "vulnerabilityId": vulnerability_id,
            "severity": severity,
            "status": status,
            "confidence": confidence,
            "evidence": evidence,
            "pocReproductionSteps": poc_reproduction_steps,
            "aiDescription": ai_description,
        }
    )
    finding_id = await core.create_finding(context_uuid, creation)
    return {"findingId": finding_id, "next_step": REVIEW_NOTE}


async def update_finding(
    core: CoreClient, context_uuid: UUID, finding_id: int, **fields: Any
) -> dict[str, Any]:
    """Send only the fields that were given, then return the finding as it is now."""
    given = {key: value for key, value in fields.items() if value is not None}
    if not given:
        raise ToolError(
            "Give at least one field to change: severity, confidence, status, evidence, "
            "poc_reproduction_steps or ai_description."
        )
    patch = FindingPatch.model_validate(given)
    await core.update_finding(context_uuid, finding_id, patch)
    try:
        return (await core.get_finding(context_uuid, finding_id)).to_output()
    except CoreError as error:
        return {
            "findingId": finding_id,
            "updated": True,
            "note": f"The update succeeded, but the finding could not be read back: {error}",
        }
