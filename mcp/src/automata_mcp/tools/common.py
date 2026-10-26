"""What every tool module shares."""

from typing import Annotated, Any
from uuid import UUID

from mcp.server.mcpserver.exceptions import ToolError
from mcp.types import ToolAnnotations
from pydantic import Field

from automata_mcp.core.models.base import CoreModel
from automata_mcp.core.paging import Page

READ_ONLY = ToolAnnotations(read_only_hint=True, open_world_hint=False)

ContextUuid = Annotated[
    str,
    Field(description="UUID of the agent context to work in. The user gives it to you."),
]


PageNumber = Annotated[int, Field(description="Page number, starting at 0.")]
PageSize = Annotated[int, Field(description="Items per page, 1 to 100 (more is cut to 100).")]


def page_output[T: CoreModel](page: Page[T], key: str) -> dict[str, Any]:
    """A page for the agent: its items under ``key``, the paging fields and, when there is more,
    ``nextPage`` (the page number to ask for next)."""
    output: dict[str, Any] = {
        key: [item.to_output() for item in page.content],
        "page": page.page,
        "size": page.size,
        "totalElements": page.total_elements,
        "totalPages": page.total_pages,
        "last": page.last,
    }
    if not page.last:
        output["nextPage"] = page.page + 1
    return output


def parse_context_uuid(value: str) -> UUID:
    """The context uuid as a ``UUID``; a value that is not one is an error the agent can fix."""
    try:
        return UUID(value)
    except ValueError:
        shown = value if len(value) <= 80 else value[:80] + "…"
        raise ToolError(
            f"context_uuid must be a UUID such as 6f1c0a52-0000-4000-8000-000000000001, got "
            f"{shown!r}. The user gives you the uuid of your context."
        ) from None
