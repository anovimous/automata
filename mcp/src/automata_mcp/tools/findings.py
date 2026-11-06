"""Tools that record, read and refine findings."""

from typing import Annotated, Any, Literal

from mcp.server.mcpserver import Context, MCPServer
from pydantic import Field

from automata_mcp.core.errors import CoreConflict, CoreNotFound
from automata_mcp.core.models.enums import FindingCreator, FindingStatus, HumanVerdict
from automata_mcp.services import findings as finding_service
from automata_mcp.state import AppState, get_state
from automata_mcp.tools.common import (
    CREATES,
    IDEMPOTENT_WRITE,
    READ_ONLY,
    ContextUuid,
    PageNumber,
    RequestId,
    hints,
    page_output,
    parse_context_uuid,
)

FindingStatusName = Literal["SUGGESTED", "CONFIRMED"]
FindingId = Annotated[int, Field(description="Id of a finding (from list_findings).")]

CREATE_404 = (
    "That request is not readable in this context, or the job is not one of your jobs that "
    "targeted it."
)
GET_404 = "Not found, or not about a request of this context."
EDIT_HINT = (
    "Only a finding you created in this context, and not yet reviewed by a human, can be edited."
)


def register(mcp: MCPServer[AppState]) -> None:
    @mcp.tool(annotations=CREATES)
    async def create_finding(
        context_uuid: ContextUuid,
        request_id: RequestId,
        vulnerability_id: Annotated[
            int, Field(description="Id of the vulnerability class (list_vulnerabilities).")
        ],
        severity: Annotated[float, Field(description="How serious it is, 0 to 10.")],
        confidence: Annotated[float, Field(description="How sure you are, 0 to 10.")],
        status: Annotated[
            FindingStatusName,
            Field(description="SUGGESTED, or CONFIRMED only with a reproducible proof."),
        ],
        evidence: Annotated[
            str, Field(description="What you observed. Mask tokens and credentials.")
        ],
        ctx: Context[AppState, Any],
        poc_reproduction_steps: Annotated[
            str | None, Field(description="Steps to reproduce it. Required for CONFIRMED.")
        ] = None,
        job_id: Annotated[
            int | None,
            Field(description="One of your own jobs on that request that produced the evidence."),
        ] = None,
        ai_description: Annotated[
            str | None, Field(description="A short description of the finding.")
        ] = None,
    ) -> dict[str, Any]:
        """Records a finding: a vulnerability you verified or suspect on a request of your context.
        A human will review it, and their verdict will appear in get_finding.

        Read list_findings first: the human verdicts on earlier findings show what is considered
        real, and you avoid duplicates. Use status SUGGESTED unless you have a reproducible proof;
        CONFIRMED needs `poc_reproduction_steps` (the core refuses it otherwise). `severity` and
        `confidence` are 0 to 10. `evidence` is what you observed: status codes, responses that
        differ, short snippets. Mask tokens and never paste a tenant's credentials into it. Pick
        `vulnerability_id` with list_vulnerabilities (the most specific class that fits). `job_id`
        is optional and must be one of your own jobs that targeted this request. The request must
        be readable in your context, which includes the older members of an equality set.

        Returns the new `findingId`. The core checks every field and its message is shown if one
        is wrong.
        """
        state = get_state(ctx)
        with hints({CoreNotFound: CREATE_404}):
            return await finding_service.create_finding(
                state.core,
                parse_context_uuid(context_uuid),
                request_id=request_id,
                vulnerability_id=vulnerability_id,
                severity=severity,
                confidence=confidence,
                status=status,
                evidence=evidence,
                poc_reproduction_steps=poc_reproduction_steps,
                job_id=job_id,
                ai_description=ai_description,
            )

    @mcp.tool(annotations=READ_ONLY)
    async def get_finding(
        context_uuid: ContextUuid, finding_id: FindingId, ctx: Context[AppState, Any]
    ) -> dict[str, Any]:
        """Returns one finding in full: request, job, vulnerability, severity, status, confidence,
        `evidence`, `pocReproductionSteps`, who created it (`createdBy`) and its `origin`
        (THIS_CONTEXT, OTHER_CONTEXT or USER), and the human review: `humanVerdict`
        (TRUE_POSITIVE or FALSE_POSITIVE), `humanComment` and `reviewedAt`.

        The human verdict and comment are the learning signal: read them to see what people
        consider real before you record a similar finding. A finding about a request outside your
        context answers "not found".
        """
        state = get_state(ctx)
        with hints({CoreNotFound: GET_404}):
            finding = await state.core.get_finding(parse_context_uuid(context_uuid), finding_id)
        return finding.to_output()

    @mcp.tool(annotations=READ_ONLY)
    async def list_findings(
        context_uuid: ContextUuid,
        ctx: Context[AppState, Any],
        request_id: Annotated[
            int | None, Field(description="Only findings of this request.")
        ] = None,
        job_id: Annotated[int | None, Field(description="Only findings from this job.")] = None,
        status: Annotated[
            FindingStatusName | None, Field(description="Only SUGGESTED or only CONFIRMED.")
        ] = None,
        human_verdict: Annotated[
            Literal["TRUE_POSITIVE", "FALSE_POSITIVE"] | None,
            Field(description="Only findings a human judged so."),
        ] = None,
        created_by: Annotated[
            Literal["AI", "USER"] | None,
            Field(description="Only findings made by the AI or by a person."),
        ] = None,
        page: PageNumber = 0,
        size: Annotated[int, Field(description="Items per page, 1 to 100.")] = 20,
    ) -> dict[str, Any]:
        """Lists the findings about every request you can read in the context, newest first,
        including those of other contexts and of people, each with its `origin`, severity, status,
        confidence and `humanVerdict`.

        Read this before creating a finding: the human verdicts show what is considered real, and
        you can see whether the same issue is already recorded. A finding can be about an older
        member of an equality set that get_request can still read (see list_context_requests).
        Filter with `request_id`, `job_id`, `status`, `human_verdict` and `created_by`. Results are
        paged: `nextPage` says which page to ask for next; get_finding gives one in full.
        """
        state = get_state(ctx)
        result = await state.core.list_findings(
            parse_context_uuid(context_uuid),
            request_id=request_id,
            job_id=job_id,
            status=FindingStatus(status) if status else None,
            human_verdict=HumanVerdict(human_verdict) if human_verdict else None,
            created_by=FindingCreator(created_by) if created_by else None,
            page=page,
            size=size,
        )
        return page_output(result, "findings")

    @mcp.tool(annotations=IDEMPOTENT_WRITE)
    async def update_finding(
        context_uuid: ContextUuid,
        finding_id: FindingId,
        ctx: Context[AppState, Any],
        severity: Annotated[float | None, Field(description="New severity, 0 to 10.")] = None,
        confidence: Annotated[float | None, Field(description="New confidence, 0 to 10.")] = None,
        status: Annotated[FindingStatusName | None, Field(description="New status.")] = None,
        evidence: Annotated[str | None, Field(description="New evidence text.")] = None,
        poc_reproduction_steps: Annotated[
            str | None, Field(description="New reproduction steps.")
        ] = None,
        ai_description: Annotated[str | None, Field(description="New description.")] = None,
    ) -> dict[str, Any]:
        """Changes a finding you created. Only the fields you give are changed; the others stay.

        Only a finding you created in this context, and that no human has reviewed yet, can be
        edited; otherwise the core refuses. Use it to add evidence, correct the severity or
        confidence, or move a SUGGESTED finding to CONFIRMED once you have reproduction steps.
        Returns the finding as it is after the change.
        """
        state = get_state(ctx)
        with hints({CoreNotFound: GET_404, CoreConflict: EDIT_HINT}):
            return await finding_service.update_finding(
                state.core,
                parse_context_uuid(context_uuid),
                finding_id,
                severity=severity,
                confidence=confidence,
                status=status,
                evidence=evidence,
                poc_reproduction_steps=poc_reproduction_steps,
                ai_description=ai_description,
            )
