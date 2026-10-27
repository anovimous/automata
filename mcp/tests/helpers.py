"""Helpers shared by the tests (importable as ``helpers``: ``tests/`` is on the path)."""

from collections.abc import AsyncIterator
from contextlib import AbstractAsyncContextManager, asynccontextmanager

import httpx
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
