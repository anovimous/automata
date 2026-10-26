"""Tools that describe the context: its briefing, configuration, host, targets and tenants."""

from typing import Annotated, Any

from mcp.server.mcpserver import Context, MCPServer
from pydantic import Field

from automata_mcp.services.briefing import build_briefing
from automata_mcp.services.targets import list_targets as list_context_targets
from automata_mcp.state import AppState, get_state
from automata_mcp.tools.common import READ_ONLY, ContextUuid, parse_context_uuid


def register(mcp: MCPServer[AppState]) -> None:
    @mcp.tool(annotations=READ_ONLY)
    async def get_context_briefing(
        context_uuid: ContextUuid, ctx: Context[AppState, Any]
    ) -> dict[str, Any]:
        """Call this first in a session. It gives your targets, tenants, host and allowed routines
        in one call, with notes that explain the situation.

        Returns `context` (name, description, representativeStrategy, defaultAutoApproval,
        defaults, maxRate), `host` (rate limits, technologies, notes), `targets` (the requests you
        can create jobs on, each with its method, path and content type), `tenants` (the identities
        of the context with their authentication data and the ids of their requests),
        `allowedRoutines` (the only routines you can use in create_job, each with its
        configSchema, whether it needs a wordlist and what it can target) and `notes`.

        Read the notes: they say how many targets are not job-eligible or need a human to approve
        a job, what the rate limits are (a host limit of 0 closes the host to jobs), and which
        routines need a wordlist. The tenants' authentication data is sensitive: never copy it
        into a finding; mask tokens.

        If a routine cannot be read, its slot in `allowedRoutines` holds an `error` instead; call
        get_routine for it. If the context itself does not exist you get an error: check the uuid
        with the user.
        """
        state = get_state(ctx)
        return await build_briefing(state.core, state.settings, parse_context_uuid(context_uuid))

    @mcp.tool(annotations=READ_ONLY)
    async def get_context(context_uuid: ContextUuid, ctx: Context[AppState, Any]) -> dict[str, Any]:
        """Returns the configuration of the context: its host id, representativeStrategy,
        defaultAutoApproval, the allowed routine ids, the tenant ids, the defaults for duration,
        verbosity and priority, maxRate, and its `items` (the requests and equality sets it is
        made of).

        Use it when you only need the configuration; get_context_briefing gives this and much
        more in one call. The defaults are what create_job falls back to when you leave
        duration, verbosity or priority out.
        """
        state = get_state(ctx)
        context = await state.core.get_context(parse_context_uuid(context_uuid))
        return context.to_output()

    @mcp.tool(annotations=READ_ONLY)
    async def get_host(context_uuid: ContextUuid, ctx: Context[AppState, Any]) -> dict[str, Any]:
        """Returns the host of the context: its name, scope, whether it is out of scope, its
        description, technologies and notes, and its rate limits (hostRateLimit, shortRateLimit,
        longRateLimit).

        Use it to understand the target's stack and to choose a job rate. A job's rate is capped
        by the host limit and by the context's maxRate; a host rate limit of 0 means the core
        refuses every job on it, and only the user can change that.
        """
        state = get_state(ctx)
        host = await state.core.get_host(parse_context_uuid(context_uuid))
        return host.to_output()

    @mcp.tool(annotations=READ_ONLY)
    async def list_targets(
        context_uuid: ContextUuid,
        ctx: Context[AppState, Any],
        with_summary: Annotated[
            bool,
            Field(description="Add each target's method, path and content type (more calls)."),
        ] = True,
    ) -> dict[str, Any]:
        """These are the requests you can create jobs on. You can also read the other members of
        their equality sets (list_context_requests), but not create jobs on them.

        Each target has `requestId`, `automataJobEligible` (false: its body is not empty, JSON or
        form, so no job can target it; test it by other means), `autoApproval` (true: a job on it
        needs no human approval; false: a human must approve the job before you can queue it),
        `sourceItemType` and `sourceEqualitySetId`. Requests of one equality set are equivalent:
        pick one representative per set. Under the LATEST strategy only the newest request of a
        set is listed; under AI_JUDGEMENT every request of the set is.

        With with_summary (the default) the targets also carry method, computatedPath, target,
        contentType, tenantId, responseId and bodySizeBytes, for as many targets as the server
        enriches (`enriched` says how many; read the rest with get_request). Turn it off for a
        quick list of ids.
        """
        state = get_state(ctx)
        return await list_context_targets(
            state.core,
            parse_context_uuid(context_uuid),
            with_summary=with_summary,
            enrich_limit=state.settings.target_enrich_limit,
        )

    @mcp.tool(annotations=READ_ONLY)
    async def list_tenants(
        context_uuid: ContextUuid, ctx: Context[AppState, Any]
    ) -> dict[str, Any]:
        """Returns the tenants (identities) of the context, each with its name, email, static
        authentication data (headers, cookies, query parameters; none when the tenant has none)
        and `requestIds`, the in-context requests that belong to it.

        Authentication data is sensitive: use it only to understand which identity a request
        belongs to or to reproduce a finding; never include it in a finding's evidence verbatim,
        mask tokens. A tenant with no requestIds is an identity you may test with (for example in
        access-control checks) although no in-context request belongs to it. A job always runs as
        the tenant of its target request; you cannot pick another one.
        """
        state = get_state(ctx)
        tenants = await state.core.list_tenants(parse_context_uuid(context_uuid))
        return {"tenants": [tenant.to_output() for tenant in tenants]}
