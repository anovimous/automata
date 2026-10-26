"""The tools of the server, one module per group (plan section 8).

Each module exposes ``register(mcp)`` that declares its tools with ``@mcp.tool``; a tool only
validates its arguments and delegates to ``services/`` and ``core/``. ``register_all`` is the one
place that lists the modules, so a phase that adds a group adds one line here.
"""

from mcp.server.mcpserver import MCPServer

from automata_mcp.state import AppState
from automata_mcp.tools import context, reference, requests, responses, wordlists


def register_all(mcp: MCPServer[AppState]) -> None:
    """Declare every tool on the server."""
    context.register(mcp)
    requests.register(mcp)
    responses.register(mcp)
    reference.register(mcp)
    wordlists.register(mcp)
