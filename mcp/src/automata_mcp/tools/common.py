"""What every tool module shares."""

from typing import Annotated
from uuid import UUID

from mcp.server.mcpserver.exceptions import ToolError
from mcp.types import ToolAnnotations
from pydantic import Field

READ_ONLY = ToolAnnotations(read_only_hint=True, open_world_hint=False)

ContextUuid = Annotated[
    str,
    Field(description="UUID of the agent context to work in. The user gives it to you."),
]


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
