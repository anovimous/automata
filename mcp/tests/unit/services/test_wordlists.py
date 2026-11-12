import json
from collections.abc import AsyncIterator

import pytest
import respx

from automata_mcp.core.client import CoreClient
from automata_mcp.core.errors import CoreBadRequest, CoreConflict, CorePayloadTooLarge
from automata_mcp.services import wordlists as wordlist_service
from helpers import multipart_parts

BASE = "http://core.test:8080"


@pytest.fixture
async def core() -> AsyncIterator[respx.MockRouter]:
    with respx.mock(base_url=BASE, assert_all_called=False) as router:
        yield router


@pytest.fixture
async def client() -> AsyncIterator[CoreClient]:
    async with CoreClient(BASE, get_retries=0) as instance:
        yield instance


async def create(client: CoreClient, **overrides: object) -> dict[str, object]:
    arguments: dict[str, object] = {
        "name": "ids.txt",
        "content": "1\n2\n3\n",
        "small_description": "Sequential ids",
        "payload_content_description": "Decimal ids, one per line",
        "vulnerability_ids": None,
    }
    return await wordlist_service.create_wordlist(client, **(arguments | overrides))  # type: ignore[arg-type]


async def test_the_text_is_uploaded_as_a_file_named_after_the_wordlist(
    client: CoreClient, core: respx.MockRouter
) -> None:
    route = core.post("/api/wordlists").respond(201, headers={"Location": "/api/wordlists/9"})

    result = await create(client, content="admin\nroot\nuser\n")

    parts = multipart_parts(route.calls.last.request)
    assert parts["file"] == ("ids.txt", "text/plain", b"admin\nroot\nuser\n")
    assert result["wordlistId"] == 9
    assert "wordlist_ids of create_job" in str(result["next_step"])


async def test_the_other_arguments_travel_as_a_json_metadata_part(
    client: CoreClient, core: respx.MockRouter
) -> None:
    route = core.post("/api/wordlists").respond(201, headers={"Location": "/api/wordlists/9"})

    await create(
        client,
        small_description="Sequential ids",
        payload_content_description="Decimal ids, one per line",
        vulnerability_ids=[12, 13],
    )

    filename, content_type, content = multipart_parts(route.calls.last.request)["metadata"]
    assert filename is None
    assert content_type == "application/json"
    assert json.loads(content) == {
        "name": "ids.txt",
        "smallDescription": "Sequential ids",
        "payloadContentDescription": "Decimal ids, one per line",
        "vulnIds": [12, 13],
    }


async def test_the_vulnerabilities_are_left_out_when_not_given(
    client: CoreClient, core: respx.MockRouter
) -> None:
    route = core.post("/api/wordlists").respond(201, headers={"Location": "/api/wordlists/9"})

    await create(client)

    assert json.loads(multipart_parts(route.calls.last.request)["metadata"][2]) == {
        "name": "ids.txt",
        "smallDescription": "Sequential ids",
        "payloadContentDescription": "Decimal ids, one per line",
    }


async def test_an_empty_list_of_vulnerabilities_is_sent_as_it_is(
    client: CoreClient, core: respx.MockRouter
) -> None:
    route = core.post("/api/wordlists").respond(201, headers={"Location": "/api/wordlists/9"})

    await create(client, vulnerability_ids=[])

    assert json.loads(multipart_parts(route.calls.last.request)["metadata"][2])["vulnIds"] == []


async def test_non_ascii_content_is_sent_as_utf8(
    client: CoreClient, core: respx.MockRouter
) -> None:
    route = core.post("/api/wordlists").respond(201, headers={"Location": "/api/wordlists/9"})

    await create(client, content="café\nñandú\n")

    assert multipart_parts(route.calls.last.request)["file"][2] == "café\nñandú\n".encode()


async def test_nothing_is_checked_here_not_even_an_empty_file(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post("/api/wordlists").respond(400, text="The wordlist file is empty")

    with pytest.raises(CoreBadRequest, match="The wordlist file is empty"):
        await create(client, content="")

    assert core.calls.call_count == 1


async def test_a_duplicate_is_the_conflict_of_the_core_with_its_message(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post("/api/wordlists").respond(409, text="Wordlist already exists with id 3")

    with pytest.raises(CoreConflict, match="already exists with id 3"):
        await create(client)


async def test_a_file_over_the_limit_of_the_core_is_its_error(
    client: CoreClient, core: respx.MockRouter
) -> None:
    core.post("/api/wordlists").respond(413, text="The uploaded file is too large")

    with pytest.raises(CorePayloadTooLarge):
        await create(client, content="x" * 1000)


async def test_a_large_list_is_sent_whole_without_a_cap_of_our_own(
    client: CoreClient, core: respx.MockRouter
) -> None:
    route = core.post("/api/wordlists").respond(201, headers={"Location": "/api/wordlists/9"})
    content = "entry\n" * 200_000

    await create(client, content=content)

    assert len(multipart_parts(route.calls.last.request)["file"][2]) == len(content)
