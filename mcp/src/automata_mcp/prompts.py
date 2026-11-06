"""The prompt that carries the testing workflow (plan section 10.1).

It is the user's "AI Test Flow": the steps the agent follows in a context. The text names the tools
of the server, so a test checks that every tool it mentions exists.
"""

from typing import Annotated
from uuid import UUID

from mcp import MCPError
from mcp.server.mcpserver import MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from mcp.types import INVALID_PARAMS
from pydantic import Field

from automata_mcp.state import AppState
from automata_mcp.tools.common import parse_context_uuid

WORKFLOW = """\
You are testing one Automata agent context: {context_uuid}. Pass this uuid as `context_uuid` to \
the tools that ask for it. Follow this flow.

1. Call `get_context_briefing` and read all of it: your targets, tenants, host, allowed routines, \
recent jobs and findings, and the notes. They tell you what you can do and what limits apply.

2. Understand each target with `get_request` and `get_response` (`get_response_body` for scripts \
and HTML). The requests of one equality set are equivalent: pick one representative per set. Under \
the LATEST strategy only the newest request of a set is a target; the older members can be read \
(`list_context_requests`) but a job cannot target them.

3. Decide what to test and why. Before creating a job on a request, read what was already tried \
with `list_request_jobs`, and do not repeat a test that ended unless you change something. For \
each test create a job with `create_job`, and write its `ai_description` as the goal: what the \
result will let us verify. Only job-eligible targets can have jobs; test the others with your own \
reasoning and, if you observe new traffic, record it with `add_request`.

4. A job may need a human to approve it (`approved` is false): tell the user and wait, you cannot \
queue it. Otherwise call `queue_job` once its config files are READY.

5. Follow your jobs with `wait_for_jobs`, not repeated `get_job` calls. When a job fails, use its \
`failureCategory` and `failureMessage`: INVALID_CONFIG means fix the config or the wordlists and \
create a new job; PREPARATION, POLL, TOOL_START and RESULT are problems of the worker, so tell the \
user or retry later with the same parameters; TOOL_EXIT means the tool exited with an error, so \
read the message and consider a lower rate or other flags instead of retrying blindly; UNKNOWN \
means read the message and retry at most once with changes. A job in state LOSTCONTACT lost its \
result: ask the user to check the worker.

6. For each finished job read `get_job_result`, then write your conclusion with \
`set_job_ai_result`: what was tested, what you saw, whether the hypothesis held, and the decision.

7. If a vulnerability is verified, record it with `create_finding`, with evidence, severity, \
confidence and, for a confirmed one, the reproduction steps. Read `list_findings` first: the human \
verdicts on earlier findings show what people consider real.

Rules: stay inside your context; never put credentials or tokens in a finding; do not repeat a \
test that already ended unless you change something; cancel (`cancel_job`) only jobs you started \
and no longer need.
"""


def workflow_text(context_uuid: UUID) -> str:
    """The workflow for one context."""
    return WORKFLOW.format(context_uuid=context_uuid)


def register(mcp: MCPServer[AppState]) -> None:
    @mcp.prompt(title="Test a context")
    def test_context(
        context_uuid: Annotated[
            str, Field(description="UUID of the agent context to test. The user gives it to you.")
        ],
    ) -> str:
        """The workflow to follow when testing an Automata agent context: read the briefing,
        understand the targets, create and follow jobs, read their results, and record findings."""
        try:
            parsed = parse_context_uuid(context_uuid)
        except ToolError as error:
            # Only an MCPError reaches the client with its message; any other is generic.
            raise MCPError(INVALID_PARAMS, str(error)) from error
        return workflow_text(parsed)
