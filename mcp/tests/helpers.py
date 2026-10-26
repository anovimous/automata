"""Helpers shared by the tests (importable as ``helpers``: ``tests/`` is on the path)."""

from mcp import Client
from mcp.types import CallToolResult, ListToolsResult

from automata_mcp.config import Settings
from automata_mcp.server import create_server


class Agent:
    """An MCP client of the real server, connected in memory, one short connection per call.

    A client held open by a fixture would be entered and exited in different tasks (an anyio
    restriction), so each call connects, calls and disconnects.
    """

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def list_tools(self) -> ListToolsResult:
        async with Client(create_server(self._settings)) as client:
            return await client.list_tools()

    async def call_tool(self, name: str, arguments: dict[str, object]) -> CallToolResult:
        async with Client(create_server(self._settings)) as client:
            return await client.call_tool(name, arguments)
