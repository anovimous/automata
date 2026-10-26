"""Tools for wordlists: reading them (creating one is added in a later phase)."""

from typing import Annotated, Any

from mcp.server.mcpserver import Context, MCPServer
from pydantic import Field

from automata_mcp.state import AppState, get_state
from automata_mcp.tools.common import READ_ONLY, PageNumber, PageSize, page_output


def register(mcp: MCPServer[AppState]) -> None:
    @mcp.tool(annotations=READ_ONLY)
    async def list_wordlists(
        ctx: Context[AppState, Any],
        query: Annotated[str, Field(description="Text to look for in the wordlist name.")] = "",
        vulnerability_id: Annotated[
            int | None, Field(description="Only the wordlists meant for this vulnerability.")
        ] = None,
        page: PageNumber = 0,
        size: PageSize = 30,
    ) -> dict[str, Any]:
        """Lists the wordlists stored in the core: id, name, description, number of lines and size.

        A job takes a list of wordlist ids (`wordlist_ids` of create_job); routines that fuzz
        (`wordlistRequired` in get_routine) need one. Pick by name and description, and filter by
        `vulnerability_id` to see the ones meant for a class of vulnerability. You cannot read
        the content of a wordlist, only its metadata. Results are paged: `nextPage` says which
        page to ask for next.
        """
        state = get_state(ctx)
        result = await state.core.list_wordlists(
            query=query, vulnerability_id=vulnerability_id, page=page, size=size
        )
        return page_output(result, "wordlists")

    @mcp.tool(annotations=READ_ONLY)
    async def get_wordlist(
        wordlist_id: Annotated[int, Field(description="Id of the wordlist.")],
        ctx: Context[AppState, Any],
    ) -> dict[str, Any]:
        """Returns one wordlist's metadata: id, name, description, number of lines, size in bytes
        and the sha256 of its content.

        Use it to check that a wordlist is what you expect before putting its id in create_job.
        The content itself cannot be read through this server.
        """
        state = get_state(ctx)
        return (await state.core.get_wordlist(wordlist_id)).to_output()
