"""Errors of the core, turned into messages the agent can act on (plan section 9.5).

The core answers errors with a plain-text body (its exception message). Each status becomes one
exception whose text is guidance followed by the core's message, cut to ``MAX_MESSAGE_CHARS``.
Tools that need a more specific hint catch the exception and use ``status`` and ``core_message``.
"""

from mcp.server.mcpserver.exceptions import ToolError

MAX_MESSAGE_CHARS = 1000


def truncate_message(text: str, limit: int = MAX_MESSAGE_CHARS) -> str:
    text = text.strip()
    return text if len(text) <= limit else text[: limit - 1] + "…"


class CoreError(ToolError):
    """An error reported by, or while talking to, the core.

    It is a ``ToolError`` on purpose: the MCP SDK shows the message of a ``ToolError`` to the
    agent, while any other exception is a crash whose text is withheld ("Error executing tool").
    """

    def __init__(self, message: str, *, status: int | None = None, core_message: str = "") -> None:
        super().__init__(message)
        self.status = status
        self.core_message = core_message


class CoreNotFound(CoreError):  # 404
    pass


class CoreBadRequest(CoreError):  # 400
    pass


class CoreConflict(CoreError):  # 409
    pass


class CoreUnprocessable(CoreError):  # 422
    pass


class CorePayloadTooLarge(CoreError):  # 413
    pass


class CoreNotImplemented(CoreError):  # 501
    pass


class CoreServerError(CoreError):  # other 5xx
    pass


class CoreRejected(CoreError):
    """Any other 4xx (for example 415)."""


class CoreUnreachable(CoreError):
    """The core did not answer: connection failed or timed out."""


class CoreResponseError(CoreError):
    """The core answered with something unreadable (not JSON, wrong shape, no Location)."""


def error_from_response(status: int, body: str) -> CoreError:
    """The exception for an error answer of the core."""
    message = truncate_message(body) or "(no message)"
    error_type: type[CoreError]
    match status:
        case 404:
            error_type = CoreNotFound
            text = (
                f"Not found, or not part of this context: {message}. "
                "Use list_targets / list_jobs to see what you can access."
            )
        case 400:
            error_type = CoreBadRequest
            text = f"The core rejected the arguments: {message}. Fix the arguments and retry."
        case 409:
            error_type = CoreConflict
            text = (
                "The core refused because of the current state (job state, approval, finding "
                f"editable, or a change made at the same time): {message}. "
                "Re-read the item before deciding what to do."
            )
        case 413:
            error_type = CorePayloadTooLarge
            text = (
                f"The wordlist is over the core's limit (512 MB): {message}. Send a smaller file."
            )
        case 422:
            error_type = CoreUnprocessable
            text = (
                f"That cannot be a job target: {message}. If the cause is the host (for example a "
                "rate limit of 0), you cannot change it: tell the user."
            )
        case 501:
            error_type = CoreNotImplemented
            text = f"Not supported by the core: {message}."
        case _ if status >= 500:
            error_type = CoreServerError
            text = (
                f"The core failed (HTTP {status}): {message}. It may be temporary; retry once. "
                "Do not retry in a loop."
            )
        case _:
            error_type = CoreRejected
            text = f"The core refused the request (HTTP {status}): {message}."
    return error_type(text, status=status, core_message=message)


def unreachable(url: str, reason: str, *, may_have_been_applied: bool) -> CoreUnreachable:
    """The core could not be reached. ``may_have_been_applied`` is for requests that change data."""
    text = f"Cannot reach the core at {url} ({reason}). Tell the user."
    if may_have_been_applied:
        text += " The request may or may not have been applied: check before repeating it."
    return CoreUnreachable(text)
