"""The testing workflow prompt: its text, its arguments and its errors."""

import re
from uuid import UUID

import pytest
from mcp import MCPError
from mcp.types import INVALID_PARAMS

from automata_mcp.prompts import WORKFLOW, workflow_text
from helpers import ALL_TOOLS, Agent

CTX = "6f1c0a52-0000-4000-8000-000000000001"
TOOL_VERBS = (
    "get",
    "list",
    "create",
    "update",
    "queue",
    "retry",
    "set",
    "cancel",
    "wait",
    "export",
    "add",
)


def mentioned_tools(text: str) -> set[str]:
    """The names between backticks that look like a tool (a verb, an underscore, a noun)."""
    names = set(re.findall(r"`([a-z]+(?:_[a-z]+)+)`", text))
    return {name for name in names if name.split("_")[0] in TOOL_VERBS}


# ---------------------------------------------------------------------------- the text


def test_the_workflow_names_the_context_it_is_for() -> None:
    text = workflow_text(UUID(CTX))

    assert CTX in text
    assert "{context_uuid}" not in text


def test_the_workflow_has_the_seven_steps_and_the_rules() -> None:
    text = workflow_text(UUID(CTX))

    for number in range(1, 8):
        assert f"\n{number}. " in "\n" + text, f"step {number}"
    assert "Rules:" in text


def test_every_tool_the_workflow_mentions_exists() -> None:
    assert mentioned_tools(WORKFLOW) <= ALL_TOOLS


def test_the_workflow_uses_the_tools_of_the_flow() -> None:
    expected = {
        "get_context_briefing",
        "get_request",
        "get_response",
        "get_response_body",
        "list_context_requests",
        "list_request_jobs",
        "create_job",
        "add_request",
        "queue_job",
        "wait_for_jobs",
        "get_job_result",
        "set_job_ai_result",
        "create_finding",
        "list_findings",
        "cancel_job",
    }

    assert expected <= mentioned_tools(WORKFLOW)


def test_the_workflow_explains_every_failure_category() -> None:
    for category in (
        "INVALID_CONFIG",
        "PREPARATION",
        "POLL",
        "TOOL_START",
        "TOOL_EXIT",
        "RESULT",
        "UNKNOWN",
        "LOSTCONTACT",
    ):
        assert category in WORKFLOW


def test_the_workflow_keeps_the_safety_rules() -> None:
    assert "stay inside your context" in WORKFLOW
    assert "never put credentials or tokens in a finding" in WORKFLOW
    assert "do not repeat a test that already ended" in WORKFLOW
    assert "cancel (`cancel_job`) only jobs you started" in WORKFLOW


# ---------------------------------------------------------------------------- through MCP


async def test_the_prompt_is_listed_with_its_argument(agent: Agent) -> None:
    prompts = (await agent.list_prompts()).prompts

    assert [p.name for p in prompts] == ["test_context"]
    prompt = prompts[0]
    assert prompt.description
    assert prompt.title == "Test a context"
    assert [(a.name, a.required) for a in prompt.arguments or []] == [("context_uuid", True)]


async def test_getting_the_prompt_gives_one_user_message_for_the_context(agent: Agent) -> None:
    result = await agent.get_prompt("test_context", {"context_uuid": CTX})

    assert len(result.messages) == 1
    message = result.messages[0]
    assert message.role == "user"
    assert message.content.text == workflow_text(UUID(CTX))  # type: ignore[union-attr]


async def test_a_context_uuid_in_capitals_is_written_in_the_usual_form(agent: Agent) -> None:
    result = await agent.get_prompt("test_context", {"context_uuid": CTX.upper()})

    assert CTX in result.messages[0].content.text  # type: ignore[union-attr]


@pytest.mark.parametrize("value", ["", "not-a-uuid", "123"])
async def test_a_value_that_is_not_a_uuid_is_refused_with_a_message_the_client_can_read(
    agent: Agent, value: str
) -> None:
    with pytest.raises(MCPError) as raised:
        await agent.get_prompt("test_context", {"context_uuid": value})

    assert raised.value.error.code == INVALID_PARAMS
    assert "context_uuid must be a UUID" in raised.value.error.message
