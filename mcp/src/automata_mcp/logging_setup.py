"""Logging of the server.

Everything goes to stderr: with the stdio transport stdout is the MCP channel and must stay clean.
Tool calls are logged at INFO with the tool name, the context uuid, the duration and the outcome;
never the arguments or the results (they can hold tenant authentication, plan section 11.4).
"""

import logging
import sys
import time
from typing import Any

from mcp.server.context import CallNext, HandlerResult, ServerRequestContext

HANDLER_NAME = "automata-mcp-stderr"
FORMAT = "%(asctime)s %(levelname)s %(name)s: %(message)s"

logger = logging.getLogger("automata_mcp.tools")


def configure_logging(level: str) -> None:
    """Send logs to stderr: ``level`` for this package, WARNING for the libraries. Idempotent."""
    root = logging.getLogger()
    if not any(handler.get_name() == HANDLER_NAME for handler in root.handlers):
        handler = logging.StreamHandler(sys.stderr)
        handler.set_name(HANDLER_NAME)
        handler.setFormatter(logging.Formatter(FORMAT))
        root.addHandler(handler)
    root.setLevel(logging.WARNING)
    logging.getLogger("automata_mcp").setLevel(level)


def _context_uuid(arguments: Any) -> str | None:
    value = arguments.get("context_uuid") if isinstance(arguments, dict) else None
    return value if isinstance(value, str) else None


def _is_error(result: HandlerResult) -> bool:
    if isinstance(result, dict):
        return bool(result.get("isError") or result.get("is_error"))
    return bool(getattr(result, "is_error", False))


async def log_tool_calls(ctx: ServerRequestContext[Any, Any], call_next: CallNext) -> HandlerResult:
    """Server middleware: one INFO line per tool call. Other requests pass through untouched."""
    if ctx.method != "tools/call":
        return await call_next(ctx)
    params = ctx.params or {}
    started = time.monotonic()
    outcome = "error"
    try:
        result = await call_next(ctx)
        outcome = "error" if _is_error(result) else "ok"
        return result
    finally:
        logger.info(
            "tool=%s context=%s outcome=%s duration_ms=%d",
            params.get("name"),
            _context_uuid(params.get("arguments")),
            outcome,
            (time.monotonic() - started) * 1000,
        )
