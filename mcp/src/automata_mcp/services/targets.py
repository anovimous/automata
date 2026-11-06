"""The targets of a context (the requests a job can be created on), enriched for the agent."""

import asyncio
from typing import Any
from uuid import UUID

from automata_mcp.core.client import CoreClient
from automata_mcp.core.errors import CoreError
from automata_mcp.core.models.context import ResolvedTarget
from automata_mcp.core.models.requests import RequestMetadata

ENRICH_CONCURRENCY = 8


def request_summary(metadata: RequestMetadata) -> dict[str, Any]:
    """The fields of a request's metadata that tell the agent what a target is."""
    summary = {
        "method": str(metadata.line.method),
        "computatedPath": metadata.line.computated_path,
        "target": metadata.line.target,
        "contentType": metadata.body.content_type,
        "tenantId": metadata.tenant_id,
        "responseId": metadata.response_id,
        "bodySizeBytes": metadata.body.size_bytes,
    }
    return {key: value for key, value in summary.items() if value is not None}


def target_notes(targets: list[ResolvedTarget]) -> list[str]:
    """What the agent should know about these targets, computed from them (nothing is decided)."""
    notes: list[str] = []
    if not targets:
        return ["The context has no targets: no job can be created in it."]
    not_eligible = sum(1 for target in targets if not target.automata_job_eligible)
    if not_eligible:
        notes.append(
            f"{not_eligible} of {len(targets)} targets are not job-eligible (their body is not "
            "empty, JSON or form): read them, but test them by other means."
        )
    needs_approval = sum(1 for target in targets if not target.auto_approval)
    if needs_approval:
        notes.append(
            f"A job on {needs_approval} of {len(targets)} targets needs a human to approve it "
            "before it can be queued; the others are approved when created."
        )
    sets = {target.source_equality_set_id for target in targets if target.source_equality_set_id}
    if sets:
        notes.append(
            f"{len(sets)} equality set(s): requests of one set are equivalent, so test one "
            "representative per set."
        )
    return notes


async def list_targets(
    core: CoreClient,
    context_uuid: UUID,
    *,
    with_summary: bool,
    enrich_limit: int,
) -> dict[str, Any]:
    """The resolved targets, each with what the core says about it and, when asked, its request.

    The first ``enrich_limit`` targets are enriched (at most ``ENRICH_CONCURRENCY`` calls at a
    time); a request whose metadata cannot be read is left plain.
    """
    targets = await core.list_resolved_targets(context_uuid)
    items = [target.to_output() for target in targets]
    notes = target_notes(targets)
    result: dict[str, Any] = {"targets": items, "total": len(items)}

    if with_summary and targets:
        semaphore = asyncio.Semaphore(ENRICH_CONCURRENCY)

        async def enrich(index: int) -> bool:
            async with semaphore:
                try:
                    metadata = await core.get_request_metadata(
                        context_uuid, targets[index].request_id
                    )
                except CoreError:
                    return False
            items[index].update(request_summary(metadata))
            return True

        done = await asyncio.gather(
            *(enrich(index) for index in range(min(enrich_limit, len(targets))))
        )
        result["enriched"] = sum(done)
        if result["enriched"] < len(targets):
            notes.append(
                f"Only {result['enriched']} of {len(targets)} targets carry their request summary; "
                "read the others with get_request."
            )
    result["notes"] = notes
    return result
