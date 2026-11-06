"""The briefing: everything the agent needs to start, gathered in one call."""

import asyncio
from typing import Any
from uuid import UUID

from automata_mcp.config import Settings
from automata_mcp.core.client import CoreClient
from automata_mcp.core.errors import CoreError
from automata_mcp.core.models.context import AgentContextDetailed, HostDetailed
from automata_mcp.core.models.enums import HumanVerdict, RepresentativeStrategy
from automata_mcp.core.models.reference import RoutineDetailed
from automata_mcp.core.models.tenants import AgentContextTenant
from automata_mcp.services.targets import list_targets

RECENT_JOBS = 10
RECENT_FINDINGS = 5


def unwrap[T](result: T | BaseException) -> T:
    """The value of a ``gather(..., return_exceptions=True)`` slot, or its exception raised."""
    if isinstance(result, BaseException):
        raise result
    return result


def context_section(context: AgentContextDetailed) -> dict[str, Any]:
    defaults = {
        "duration": context.default_duration,
        "verbosity": context.default_verbosity,
        "priority": context.default_priority,
    }
    section: dict[str, Any] = {
        "uuid": context.uuid,
        "name": context.name,
        "description": context.description,
        "representativeStrategy": context.representative_strategy,
        "defaultAutoApproval": context.default_auto_approval,
        "defaults": {key: value for key, value in defaults.items() if value is not None},
        "maxRate": context.max_rate,
        "defaultJobRate": context.default_job_rate,
    }
    return {key: value for key, value in section.items() if value is not None}


def routine_section(routine: RoutineDetailed) -> dict[str, Any]:
    section = routine.to_output()
    return {
        key: section[key]
        for key in (
            "id",
            "key",
            "description",
            "resultContentType",
            "configSchema",
            "wordlistRequired",
            "isAvailableAtConsumer",
            "allowedTargets",
        )
        if key in section
    }


async def fetch_routine(core: CoreClient, routine_id: int) -> dict[str, Any]:
    """A routine of the context, or ``{"id", "error"}`` when it cannot be read (not fatal)."""
    try:
        return routine_section(await core.get_routine(routine_id))
    except CoreError as error:
        return {"id": routine_id, "error": str(error)}


def strategy_note(strategy: RepresentativeStrategy) -> str | None:
    if strategy == RepresentativeStrategy.LATEST:
        return (
            "Strategy LATEST: only the newest request of each equality set is a target; the older "
            "members are readable (list_context_requests) but a job cannot target them."
        )
    if strategy == RepresentativeStrategy.AI_JUDGEMENT:
        return (
            "Strategy AI_JUDGEMENT: every request of an equality set is a target; pick one "
            "representative per set."
        )
    return None


def briefing_notes(
    context: AgentContextDetailed,
    host: HostDetailed,
    tenants: list[AgentContextTenant],
    routines: list[dict[str, Any]],
) -> list[str]:
    """What the situation means for the agent, computed from the data (nothing is decided)."""
    notes: list[str] = []
    strategy = strategy_note(context.representative_strategy)
    if strategy:
        notes.append(strategy)
    if host.host_rate_limit == 0:
        notes.append(
            "The host has a rate limit of 0: the core refuses every job on it. Tell the user; "
            "you cannot change it."
        )
    else:
        limits = (
            f"host rate limit {host.host_rate_limit}, short rate limit {host.short_rate_limit} "
            f"(SHORT jobs), long rate limit {host.long_rate_limit} (LONG jobs)"
        )
        if context.max_rate is not None:
            limits += f", context maxRate {context.max_rate}"
        notes.append(
            f"A rate you give is refused when it is above the {limits}, or above the rate limit of "
            "the program (you cannot see it)."
        )
        if context.default_job_rate is not None:
            notes.append(
                f"A job without a rate gets the context's default {context.default_job_rate}, "
                "lowered to those limits."
            )
    if host.out_of_scope:
        notes.append("The core marks this host as out of scope.")
    if tenants:
        notes.append(
            "Tenant authentication data is sensitive: use it to understand which identity a "
            "request belongs to or to reproduce a finding, and mask tokens in any evidence."
        )
        idle = [
            tenant.name or str(tenant.tenant_id) for tenant in tenants if not tenant.request_ids
        ]
        if idle:
            notes.append(
                f"Tenants without an in-context request ({', '.join(idle)}) are identities you may "
                "test with, for example in access-control checks."
            )
    need_wordlist = [routine["key"] for routine in routines if routine.get("wordlistRequired")]
    if need_wordlist:
        notes.append(
            f"Routines meant to run with a wordlist: {', '.join(need_wordlist)}. Choose one with "
            "list_wordlists (or create_wordlist); the core does not enforce it, but such a job "
            "fails at the worker without one."
        )
    unavailable = [
        routine["key"]
        for routine in routines
        if "key" in routine and routine.get("isAvailableAtConsumer") is False
    ]
    if unavailable:
        notes.append(
            f"Not available at the worker, so jobs on them are refused: {', '.join(unavailable)}."
        )
    failed = [routine["id"] for routine in routines if "error" in routine]
    if failed:
        notes.append(f"Routines that could not be read: {failed}. Try get_routine on them.")
    return notes


async def build_briefing(
    core: CoreClient, settings: Settings, context_uuid: UUID
) -> dict[str, Any]:
    """The context, host, targets, tenants, allowed routines, recent jobs and findings, with notes.

    The context, host, targets and tenants are required: their error is raised. A routine, the
    recent jobs or the recent findings that cannot be read are reported in their own slot.
    """
    results = await asyncio.gather(
        core.get_context(context_uuid),
        core.get_host(context_uuid),
        list_targets(
            core, context_uuid, with_summary=True, enrich_limit=settings.target_enrich_limit
        ),
        core.list_tenants(context_uuid),
        core.list_jobs(context_uuid, page=0, size=RECENT_JOBS),
        core.list_findings(context_uuid, page=0, size=RECENT_FINDINGS),
        return_exceptions=True,
    )
    context = unwrap(results[0])
    host = unwrap(results[1])
    targets = unwrap(results[2])
    tenants = unwrap(results[3])
    recent_jobs = results[4]
    recent_findings = results[5]
    routines = list(
        await asyncio.gather(*(fetch_routine(core, rid) for rid in context.allowed_routine_ids))
    )

    notes = list(targets["notes"])
    notes += briefing_notes(context, host, tenants, routines)
    briefing: dict[str, Any] = {
        "context": context_section(context),
        "host": host.to_output(),
        "targets": targets["targets"],
        "tenants": [tenant.to_output() for tenant in tenants],
        "allowedRoutines": routines,
    }
    if isinstance(recent_jobs, CoreError):
        briefing["recentJobs"] = {"error": str(recent_jobs)}
    elif isinstance(recent_jobs, BaseException):
        raise recent_jobs
    else:
        briefing["recentJobs"] = [job.to_output() for job in recent_jobs.content]
        waiting = [job.id for job in recent_jobs.content if job.waiting_for_action]
        if waiting:
            notes.append(
                f"Jobs waiting for someone: {waiting}. A human must approve them, or their "
                "config files failed (retry_job_preparation); see get_job."
            )
    if isinstance(recent_findings, CoreError):
        briefing["recentFindings"] = {"error": str(recent_findings)}
    elif isinstance(recent_findings, BaseException):
        raise recent_findings
    else:
        briefing["recentFindings"] = {
            "total": recent_findings.total_elements,
            "items": [finding.to_output() for finding in recent_findings.content],
        }
        judged = [f.human_verdict for f in recent_findings.content if f.human_verdict is not None]
        if judged:
            true_positives = sum(1 for verdict in judged if verdict == HumanVerdict.TRUE_POSITIVE)
            notes.append(
                f"{len(judged)} of the {len(recent_findings.content)} latest findings have a "
                f"human verdict ({true_positives} true positive, {len(judged) - true_positives} "
                "false positive): read them with get_finding before recording a similar one."
            )
    briefing["notes"] = notes
    return briefing
