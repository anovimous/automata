from uuid import UUID

import pytest
from mcp.server.mcpserver.exceptions import ToolError

from automata_mcp.core.models.reference import VulnerabilitySummary
from automata_mcp.core.paging import Page
from automata_mcp.tools.common import READ_ONLY, page_output, parse_context_uuid


def test_a_uuid_is_parsed() -> None:
    assert parse_context_uuid("6f1c0a52-0000-4000-8000-000000000001") == UUID(
        "6f1c0a52-0000-4000-8000-000000000001"
    )


def test_a_uuid_in_upper_case_or_without_dashes_is_accepted() -> None:
    expected = UUID("6f1c0a52-0000-4000-8000-000000000001")

    assert parse_context_uuid("6F1C0A52-0000-4000-8000-000000000001") == expected
    assert parse_context_uuid("6f1c0a52000040008000000000000001") == expected


@pytest.mark.parametrize("value", ["", "not-a-uuid", "12345", "6f1c0a52-0000-4000-8000"])
def test_anything_else_is_an_error_the_agent_can_act_on(value: str) -> None:
    with pytest.raises(ToolError, match="context_uuid must be a UUID") as raised:
        parse_context_uuid(value)

    assert "The user gives you" in str(raised.value)


def test_a_very_long_value_is_cut_in_the_message() -> None:
    with pytest.raises(ToolError) as raised:
        parse_context_uuid("x" * 500)

    assert len(str(raised.value)) < 300
    assert "…" in str(raised.value)


def test_read_only_annotations() -> None:
    assert READ_ONLY.read_only_hint is True
    assert READ_ONLY.open_world_hint is False


def _page(last: bool, number: int = 0) -> Page[VulnerabilitySummary]:
    return Page[VulnerabilitySummary](
        content=[VulnerabilitySummary(id=1, name="BOLA")],
        page=number,
        size=30,
        total_elements=61,
        total_pages=3,
        last=last,
    )


def test_page_output_names_the_items_and_carries_the_paging_fields() -> None:
    output = page_output(_page(last=True, number=2), "vulnerabilities")

    assert output == {
        "vulnerabilities": [{"id": 1, "name": "BOLA"}],
        "page": 2,
        "size": 30,
        "totalElements": 61,
        "totalPages": 3,
        "last": True,
    }


def test_page_output_says_which_page_comes_next_only_when_there_is_one() -> None:
    assert page_output(_page(last=False, number=1), "v")["nextPage"] == 2
    assert "nextPage" not in page_output(_page(last=True), "v")


def test_with_hint_keeps_the_class_status_and_core_message() -> None:
    from automata_mcp.core.errors import CoreNotFound, error_from_response
    from automata_mcp.tools.common import with_hint

    original = error_from_response(404, "Request has no body")

    hinted = with_hint(original, "It may have no body.")

    assert type(hinted) is CoreNotFound
    assert str(hinted).startswith(str(original))
    assert str(hinted).endswith("It may have no body.")
    assert hinted.status == 404
    assert hinted.core_message == "Request has no body"
