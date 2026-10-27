"""Creating a wordlist from text.

The core stores the file itself and enforces every rule (not empty, no NUL byte, at most 512 MB, not
a duplicate). The server adds no check and no size cap of its own: it sends the text as the file
part of the upload and the other arguments as the ``metadata`` part.
"""

from typing import Any

from automata_mcp.core.client import CoreClient
from automata_mcp.core.models.reference import WordlistCreation


async def create_wordlist(
    core: CoreClient,
    *,
    name: str,
    content: str,
    description: str | None,
    vulnerability_ids: list[int] | None,
) -> dict[str, Any]:
    """Upload the wordlist (the file is named ``name``) and return its id."""
    metadata = WordlistCreation(name=name, description=description, vuln_ids=vulnerability_ids)
    wordlist_id = await core.create_wordlist(content, file_name=name, metadata=metadata)
    return {
        "wordlistId": wordlist_id,
        "next_step": "Put this id in the wordlist_ids of create_job; get_wordlist shows what was "
        "stored.",
    }
