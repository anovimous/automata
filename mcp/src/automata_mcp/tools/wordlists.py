"""Tools for wordlists: reading them and creating one."""

from typing import Annotated, Any

from mcp.server.mcpserver import Context, MCPServer
from pydantic import Field

from automata_mcp.core.errors import CoreConflict
from automata_mcp.services import wordlists as wordlist_service
from automata_mcp.state import AppState, get_state
from automata_mcp.tools.common import (
    CREATES,
    READ_ONLY,
    PageNumber,
    PageSize,
    hints,
    page_output,
)

DUPLICATE_409 = (
    "A wordlist with exactly this content already exists (its id is in the message): use it "
    "instead of creating another."
)


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

    @mcp.tool(annotations=CREATES)
    async def create_wordlist(
        name: Annotated[
            str, Field(description="Name of the wordlist; also the name of the stored file.")
        ],
        content: Annotated[
            str, Field(description="The entries, one per line, as plain text (no NUL bytes).")
        ],
        ctx: Context[AppState, Any],
        description: Annotated[
            str | None, Field(description="What the list is for, so it can be chosen later.")
        ] = None,
        vulnerability_ids: Annotated[
            list[int] | None,
            Field(description="Ids of the vulnerabilities it is meant for (list_vulnerabilities)."),
        ] = None,
    ) -> dict[str, Any]:
        """Creates a wordlist from the text you give, to use in the `wordlist_ids` of create_job
        (for example parameter names from export_body_property_names, or ids and paths you
        collected while testing).

        `content` is the list, one entry per line. The content can not be changed afterwards (only
        the name, description and vulnerabilities can); to change it, create a new wordlist. Give
        a `description` so the list can be chosen later with list_wordlists, and the
        `vulnerability_ids` it is meant for. The core stores the file and checks it: it refuses an
        empty file, a file with a NUL byte and one over 512 MB, and a wordlist whose content
        already exists (then the message holds its id: use that one). Over HTTP a single call is
        also limited by the server's request size (4 MB by default), so keep the list reasonable.

        Returns the new `wordlistId`.
        """
        state = get_state(ctx)
        with hints({CoreConflict: DUPLICATE_409}):
            return await wordlist_service.create_wordlist(
                state.core,
                name=name,
                content=content,
                description=description,
                vulnerability_ids=vulnerability_ids,
            )
