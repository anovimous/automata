"""Tools that read the reference data of the core: routines and vulnerabilities."""

from typing import Annotated, Any

from mcp.server.mcpserver import Context, MCPServer
from pydantic import Field

from automata_mcp.state import AppState, get_state
from automata_mcp.tools.common import READ_ONLY, PageNumber, PageSize, page_output


def register(mcp: MCPServer[AppState]) -> None:
    @mcp.tool(annotations=READ_ONLY)
    async def list_routines(
        ctx: Context[AppState, Any],
        query: Annotated[str, Field(description="Text to look for in the routine key.")] = "",
        vulnerability_id: Annotated[
            int | None, Field(description="Only the routines that test this vulnerability.")
        ] = None,
        page: PageNumber = 0,
        size: PageSize = 30,
    ) -> dict[str, Any]:
        """Lists the routines the core knows (a routine is a kind of job, such as `fuzz`), with
        their key, whether they need a wordlist, their overhead and protocol, what they can
        target and whether the worker offers them.

        Only the routines in your context's allowedRoutineIds can be used in create_job; the
        briefing (get_context_briefing) already lists those with their details. Use this to see
        what else exists, and get_routine to read one routine's description and `configSchema`.
        Results are paged: `nextPage` says which page to ask for next.
        """
        state = get_state(ctx)
        result = await state.core.list_routines(
            query=query, vulnerability_id=vulnerability_id, page=page, size=size
        )
        return page_output(result, "routines")

    @mcp.tool(annotations=READ_ONLY)
    async def get_routine(
        routine_id: Annotated[int, Field(description="Id of the routine.")],
        ctx: Context[AppState, Any],
    ) -> dict[str, Any]:
        """Returns one routine in full: its key, description, `configSchema` (the JSON schema of
        the `custom_config` that create_job accepts for it), `wordlistRequired`,
        `resultContentType` (how get_job_result reads: JSON or text), `allowedTargets`,
        `overhead`, `protocol`, the vulnerability it tests and `isAvailableAtConsumer` (false:
        the worker does not offer it, so jobs on it are refused).

        Read the configSchema before building a `custom_config`. A routine with
        `wordlistRequired` is meant to run with a wordlist: the core does not enforce it, but
        such a job fails at the worker without one. You can only create jobs with the routines
        in your context's allowedRoutineIds.
        """
        state = get_state(ctx)
        return (await state.core.get_routine(routine_id)).to_output()

    @mcp.tool(annotations=READ_ONLY)
    async def list_vulnerabilities(
        ctx: Context[AppState, Any],
        query: Annotated[
            str, Field(description="Text to look for in the vulnerability name.")
        ] = "",
        parent_id: Annotated[
            int | None, Field(description="Only the children of this vulnerability.")
        ] = None,
        page: PageNumber = 0,
        size: PageSize = 30,
    ) -> dict[str, Any]:
        """Lists the vulnerability classes of the core (id and name), optionally only the
        children of one class or those whose name contains a text.

        Use it to find the `vulnerability_id` that create_finding needs: pick the most specific
        class that describes what you found. Results are paged: `nextPage` says which page to
        ask for next.
        """
        state = get_state(ctx)
        result = await state.core.list_vulnerabilities(
            query=query, parent_id=parent_id, page=page, size=size
        )
        return page_output(result, "vulnerabilities")

    @mcp.tool(annotations=READ_ONLY)
    async def get_vulnerability(
        vulnerability_id: Annotated[int, Field(description="Id of the vulnerability class.")],
        ctx: Context[AppState, Any],
    ) -> dict[str, Any]:
        """Returns one vulnerability class: its id, name and the id of its parent class (if any).

        Use it to place a class in the hierarchy when choosing the `vulnerability_id` of a
        finding; list_vulnerabilities with `parent_id` lists its siblings or children.
        """
        state = get_state(ctx)
        return (await state.core.get_vulnerability(vulnerability_id)).to_output()
