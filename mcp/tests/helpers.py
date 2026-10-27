"""Helpers shared by the tests (importable as ``helpers``: ``tests/`` is on the path)."""

from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import AbstractAsyncContextManager, asynccontextmanager

import httpx
from mcp import Client, MCPError
from mcp.types import (
    CallToolResult,
    GetPromptResult,
    ListPromptsResult,
    ListResourceTemplatesResult,
    ListToolsResult,
    ReadResourceResult,
)

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

    async def list_prompts(self) -> ListPromptsResult:
        async with Client(create_server(self._settings)) as client:
            return await client.list_prompts()

    async def get_prompt(self, name: str, arguments: dict[str, str]) -> GetPromptResult:
        return await self._raising(lambda client: client.get_prompt(name, arguments))

    async def list_resource_templates(self) -> ListResourceTemplatesResult:
        async with Client(create_server(self._settings)) as client:
            return await client.list_resource_templates()

    async def read_resource(self, uri: str) -> ReadResourceResult:
        return await self._raising(lambda client: client.read_resource(uri))

    async def _raising[T](self, call: Callable[[Client], Awaitable[T]]) -> T:
        """Run one call and let its ``MCPError`` out as itself.

        An exception that leaves the ``async with Client`` block is wrapped by anyio in an
        ``ExceptionGroup``, so the error is caught inside the block and raised after it.
        """
        failure: MCPError | None = None
        async with Client(create_server(self._settings)) as client:
            try:
                return await call(client)
            except MCPError as error:
                failure = error
        raise failure


class ChunkStream(httpx.AsyncByteStream):
    """A response body that arrives in the given chunks."""

    def __init__(self, chunks: list[bytes]) -> None:
        self._chunks = chunks

    async def __aiter__(self) -> AsyncIterator[bytes]:
        for chunk in self._chunks:
            yield chunk


def chunked_response(
    chunks: list[bytes], headers: dict[str, str] | None = None, status: int = 200
) -> httpx.Response:
    """A streamed response whose body arrives in ``chunks`` (so chunk boundaries can be tested)."""
    return httpx.Response(status, headers=headers, stream=ChunkStream(chunks))


def stream_of(response: httpx.Response) -> AbstractAsyncContextManager[httpx.Response]:
    """What ``CoreClient.stream_*`` returns, around a prepared response."""

    @asynccontextmanager
    async def opened() -> AsyncIterator[httpx.Response]:
        yield response

    return opened()


def multipart_parts(request: httpx.Request) -> dict[str, tuple[str | None, str | None, bytes]]:
    """The parts of a multipart request: name -> (file name, content type, content)."""
    from email.parser import BytesParser
    from email.policy import HTTP

    header = f"Content-Type: {request.headers['content-type']}\r\n\r\n".encode()
    message = BytesParser(policy=HTTP).parsebytes(header + request.content)
    parts: dict[str, tuple[str | None, str | None, bytes]] = {}
    for part in message.iter_parts():
        name = part.get_param("name", header="content-disposition")
        content_type = part.get_content_type() if part["content-type"] else None
        parts[str(name)] = (part.get_filename(), content_type, part.get_payload(decode=True))
    return parts


# The 37 tools of the plan.
ALL_TOOLS = frozenset(
    [
        "get_context_briefing",
        "get_context",
        "get_host",
        "list_targets",
        "list_tenants",
        "get_request",
        "get_request_raw",
        "get_request_body",
        "get_response",
        "get_response_raw",
        "get_response_body",
        "create_job",
        "list_jobs",
        "get_job",
        "wait_for_jobs",
        "get_job_result",
        "queue_job",
        "retry_job_preparation",
        "set_job_ai_result",
        "cancel_job",
        "list_routines",
        "get_routine",
        "list_vulnerabilities",
        "get_vulnerability",
        "list_wordlists",
        "get_wordlist",
        "create_wordlist",
        "create_finding",
        "get_finding",
        "list_findings",
        "update_finding",
        "export_body_property_names",
        "add_request",
        "list_request_jobs",
        "get_request_job",
        "get_request_job_result",
        "list_context_requests",
    ]
)
