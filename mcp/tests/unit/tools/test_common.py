from uuid import UUID

import pytest
from mcp.server.mcpserver.exceptions import ToolError

from automata_mcp.tools.common import READ_ONLY, parse_context_uuid


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
