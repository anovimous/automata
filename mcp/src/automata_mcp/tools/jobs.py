"""Tools that create, follow and finish jobs."""

from typing import Annotated, Any, Literal

from mcp.server.mcpserver import Context, MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from pydantic import Field

from automata_mcp.core.errors import CoreConflict, CoreNotFound
from automata_mcp.services import jobs as job_service
from automata_mcp.services.waiting import wait_for_jobs as wait_for
from automata_mcp.state import AppState, get_state
from automata_mcp.tools.common import (
    CREATES,
    DESTRUCTIVE,
    IDEMPOTENT_WRITE,
    READ_ONLY,
    BodyMaxBytes,
    BodyOffset,
    ContextUuid,
    PageNumber,
    RequestId,
    hints,
    page_output,
    parse_context_uuid,
)

JobStateName = Literal[
    "DRAFT",
    "TOQUEUE",
    "SCHEDULED",
    "QUEUED",
    "RUNNING",
    "PAUSED",
    "CANCELED",
    "FINISHED",
    "FAILED",
    "SELFTERMINATED",
    "LOSTCONTACT",
]
JobId = Annotated[
    int, Field(description="Id of a job (from create_job, list_jobs or the briefing).")
]
States = Annotated[
    list[JobStateName] | None, Field(description="Only the jobs in one of these states.")
]
JobsPageSize = Annotated[int, Field(description="Items per page, 1 to 100.")]

CREATE_404 = (
    "Here a 404 means the request is not one of your targets (call list_targets) or a wordlist "
    "id does not exist (call list_wordlists)."
)
QUEUE_409 = (
    "Possible causes: the job is not approved yet (a human must approve it), its config files "
    "are not READY (PREPARING: wait; FAILED: call retry_job_preparation), or it is no longer a "
    "DRAFT. get_job shows approved, configFileStatus and currentState."
)
RETRY_409 = (
    "Only a DRAFT job whose config files are PREPARING (stuck) or FAILED can be retried; get_job "
    "shows currentState and configFileStatus."
)
AI_RESULT_409 = (
    "The job must have ended before you can record a result; use wait_for_jobs, then try again."
)
CANCEL_409 = (
    "Only a job in TOQUEUE, QUEUED, RUNNING or PAUSED can be canceled. A draft you no longer "
    "want you simply do not queue; an ended job is final. get_job shows the state."
)
RESULT_409 = "The job has not ended: use wait_for_jobs, then read the result again."
RESULT_404 = (
    "No result: the job is not one of this context's AI jobs, or it ended without one (FAILED, "
    "CANCELED, ...). Use get_job to see its state and failure message."
)
REQUEST_JOB_404 = (
    "That job did not target this request, or the request is not one you can read in the "
    "context (list_context_requests)."
)
REQUEST_JOBS_404 = "The request must be one you can read in the context (list_context_requests)."


def register(mcp: MCPServer[AppState]) -> None:
    @mcp.tool(annotations=CREATES)
    async def create_job(
        context_uuid: ContextUuid,
        request_id: RequestId,
        routine_id: Annotated[
            int, Field(description="Id of a routine of your context (see get_context_briefing).")
        ],
        ai_description: Annotated[
            str,
            Field(
                description="What the result will let us verify (at most 1000 characters). "
                "Shown to the humans who approve the job."
            ),
        ],
        wordlist_ids: Annotated[
            list[int],
            Field(description="Ids of the wordlists the job uses (list_wordlists); may be empty."),
        ],
        ctx: Context[AppState, Any],
        duration: Annotated[
            Literal["SHORT", "LONG"] | None,
            Field(description="How long the job may run; the context's default when left out."),
        ] = None,
        rate: Annotated[
            int | None, Field(description="Requests per second; the host limit when left out.")
        ] = None,
        verbosity: Annotated[
            Literal["SUMMARIZED", "NORMAL", "VERBOSE"] | None,
            Field(description="How much the result contains; the context's default if left out."),
        ] = None,
        priority: Annotated[
            int | None, Field(description="1 to 5; the context's default when left out.")
        ] = None,
        custom_config: Annotated[
            dict[str, Any] | None,
            Field(description="Settings of the routine, as its configSchema (get_routine) says."),
        ] = None,
    ) -> dict[str, Any]:
        """Creates a job: one test of one target request with one routine. The job starts as a
        DRAFT; nothing runs until it is approved and you queue it (queue_job).

        Before creating a job on a request, call list_request_jobs to see what was already tried
        and do not repeat a test that ended unless you change the parameters. Pick the routine
        from `allowedRoutines` in get_context_briefing and read its `configSchema` with
        get_routine: `custom_config` must follow it. For a routine whose schema has
        `rawRequestBase64` (such as `fuzz`) you may give `rawRequestText` instead, the raw request
        as text with the FUZZ keyword where the wordlist entries go: the server converts it (line
        endings are fixed; Content-Length is not recomputed). A routine with `wordlistRequired` is
        meant to run with a wordlist; the core does not enforce it, but such a job fails at the
        worker without one.

        `rate` is at least 1 and is refused when it is above the host limit, the limit of the
        duration (short or long), the program limit or the context's maxRate; without a `rate`
        the job gets the context's defaultJobRate, lowered to those limits. A host with a rate
        limit of 0 refuses every job (tell the user). `duration` is needed unless the
        context has a default. `priority` is 1 to 5. A job always runs as the tenant of its target
        request. The core checks every rule and its message is shown if one is broken (the target
        must be in list_targets, job-eligible and of the context's host; the routine must be
        allowed in the context and offered by the worker).

        Returns `jobId`, `state`, `approved`, `configFileStatus`, a short `summary` and
        `next_step`: queue_job if approved and ready, or tell the user a person must approve it.
        """
        state = get_state(ctx)
        with hints({CoreNotFound: CREATE_404}):
            return await job_service.create_job(
                state.core,
                parse_context_uuid(context_uuid),
                request_id=request_id,
                routine_id=routine_id,
                ai_description=ai_description,
                wordlist_ids=wordlist_ids,
                duration=duration,
                rate=rate,
                verbosity=verbosity,
                priority=priority,
                custom_config=custom_config,
            )

    @mcp.tool(annotations=READ_ONLY)
    async def list_jobs(
        context_uuid: ContextUuid,
        ctx: Context[AppState, Any],
        states: States = None,
        page: PageNumber = 0,
        size: JobsPageSize = 20,
    ) -> dict[str, Any]:
        """Lists the AI jobs of your context, newest first, with their state, routine, rate,
        priority, `approved`, `terminal` and `waitingForAction`.

        Only the jobs created through this context by the AI are listed (use list_request_jobs
        for the history of one request, which also shows jobs of other contexts and of users).
        Filter with `states`, for example FINISHED and FAILED to find jobs whose result you have
        not read. Results are paged: `nextPage` says which page to ask for next.
        """
        state = get_state(ctx)
        result = await state.core.list_jobs(
            parse_context_uuid(context_uuid), states=states, page=page, size=size
        )
        return page_output(result, "jobs")

    @mcp.tool(annotations=READ_ONLY)
    async def get_job(
        context_uuid: ContextUuid, job_id: JobId, ctx: Context[AppState, Any]
    ) -> dict[str, Any]:
        """Returns one job of your context in full: state, requested state, routine, rate,
        priority, target, custom config, `approved`, `configFileStatus`, `aiDescription`,
        `aiResult` and, after a failure, `failureCategory` and `failureMessage`.

        Lifecycle: create_job makes a DRAFT (`approved` true or false). If it is not approved a
        human must approve it. queue_job needs `approved` and configFileStatus READY and moves it
        to TOQUEUE; the core's scheduler (about every 60 seconds) moves it to QUEUED when the
        rate limits allow, the worker runs it (RUNNING) and it ends as FINISHED, FAILED,
        SELFTERMINATED or CANCELED (LOSTCONTACT if the worker stays silent for 30 minutes). Then
        read get_job_result and record your conclusion with set_job_ai_result.

        `terminal` is true once the job ended for good. `waitingForAction` is true for a DRAFT that
        will not move by itself: nobody approved it yet (a human must), or its config files failed
        (configFileStatus FAILED: call retry_job_preparation).

        Failure categories (FAILED or SELFTERMINATED jobs): INVALID_CONFIG - the job's details
        could not be turned into a command (unknown flag, no wordlist for ffuf, unreadable
        config): fix custom_config or the wordlists and create a new job, an unchanged retry fails
        again. PREPARATION, POLL, TOOL_START, RESULT - the worker side failed, not your mistake:
        tell the user, or retry later with the same parameters. TOOL_EXIT - the tool ran and
        exited with an error, the reason is not known: read failureMessage, consider a lower rate
        or other flags, do not retry blindly. UNKNOWN - nothing more is known: read the message,
        retry at most once with changes. State LOSTCONTACT means the worker stopped reporting and
        the result is lost: ask the user to check the worker.
        """
        state = get_state(ctx)
        job = await state.core.get_job(parse_context_uuid(context_uuid), job_id)
        return job.to_output()

    @mcp.tool(annotations=READ_ONLY)
    async def list_request_jobs(
        context_uuid: ContextUuid,
        request_id: RequestId,
        ctx: Context[AppState, Any],
        states: States = None,
        page: PageNumber = 0,
        size: JobsPageSize = 20,
    ) -> dict[str, Any]:
        """Lists every job that ever targeted one request, newest first, also those of other
        contexts and of users, with their state, routine, rate, `terminal` and `origin`.

        Call this before create_job on a request to see what was already tried, with which
        routine, and how it ended; do not repeat a test that already finished unless you change
        the parameters. `origin` tells whose job it is: THIS_CONTEXT, OTHER_CONTEXT or USER. Jobs
        of other contexts and of users are read only for you (you cannot cancel or queue them).
        Wide jobs are not included. Results are paged: `nextPage` says which page comes next.
        """
        state = get_state(ctx)
        with hints({CoreNotFound: REQUEST_JOBS_404}):
            result = await state.core.list_request_jobs(
                parse_context_uuid(context_uuid), request_id, states=states, page=page, size=size
            )
        return page_output(result, "jobs")

    @mcp.tool(annotations=READ_ONLY)
    async def get_request_job(
        context_uuid: ContextUuid,
        request_id: RequestId,
        job_id: JobId,
        ctx: Context[AppState, Any],
    ) -> dict[str, Any]:
        """Returns one job from the history of a request: its state, `customConfig` (the flags or
        raw request it used), `aiDescription`, `aiResult`, failure category and message, and
        `origin`.

        Use it to learn exactly what a past test did before designing the next one. For a job of
        your own context get_job gives the full state. A job that did not target this request, or
        a request outside your context, answers "not found".
        """
        state = get_state(ctx)
        with hints({CoreNotFound: REQUEST_JOB_404}):
            job = await state.core.get_request_job(
                parse_context_uuid(context_uuid), request_id, job_id
            )
        return job.to_output()

    @mcp.tool(annotations=READ_ONLY)
    async def get_request_job_result(
        context_uuid: ContextUuid,
        request_id: RequestId,
        job_id: JobId,
        ctx: Context[AppState, Any],
        offset: BodyOffset = 0,
        max_bytes: BodyMaxBytes = None,
    ) -> dict[str, Any]:
        """Reads the result of a job from the history of a request, in parts: `max_bytes` bytes
        from `offset`. It works like get_job_result.

        Results of jobs of other contexts may contain material from other identities: mask tokens
        when you quote them in a finding. A job that has not ended answers with an error telling
        you to wait; a job that ended without a result (FAILED, CANCELED, ...) answers "not found".
        """
        state = get_state(ctx)
        settings = state.settings
        with hints({CoreConflict: RESULT_409, CoreNotFound: REQUEST_JOB_404 + " " + RESULT_404}):
            return await job_service.read_result(
                state.core.stream_request_job_result(
                    parse_context_uuid(context_uuid), request_id, job_id
                ),
                offset=offset,
                max_bytes=max_bytes,
                default_max_bytes=settings.result_max_bytes,
                fetch_cap=settings.body_max_fetch_bytes,
            )

    @mcp.tool(annotations=READ_ONLY)
    async def get_job_result(
        context_uuid: ContextUuid,
        job_id: JobId,
        ctx: Context[AppState, Any],
        offset: BodyOffset = 0,
        max_bytes: BodyMaxBytes = None,
    ) -> dict[str, Any]:
        """Reads the result of a job of your context, in parts: `max_bytes` bytes from `offset`.

        Returns `contentType` (the routine's result is JSON or plain text), `text`, `offset`,
        `returnedBytes`, `totalBytes`, `truncated` and, when truncated, `nextOffset`: call again
        with it to read on. The result is the raw output of the tool (for fuzzing, ffuf's JSON),
        so read it for what it shows, not as a verdict. A job that has not ended answers with an
        error telling you to use wait_for_jobs; a job that ended without a result (FAILED,
        CANCELED, ...) answers "not found": get_job shows its state and failure message. After
        reading, record your conclusion with set_job_ai_result.
        """
        state = get_state(ctx)
        settings = state.settings
        with hints({CoreConflict: RESULT_409, CoreNotFound: RESULT_404}):
            return await job_service.read_result(
                state.core.stream_job_result(parse_context_uuid(context_uuid), job_id),
                offset=offset,
                max_bytes=max_bytes,
                default_max_bytes=settings.result_max_bytes,
                fetch_cap=settings.body_max_fetch_bytes,
            )

    @mcp.tool(annotations=READ_ONLY)
    async def wait_for_jobs(
        context_uuid: ContextUuid,
        job_ids: Annotated[
            list[int], Field(min_length=1, description="Ids of the jobs to wait for.")
        ],
        ctx: Context[AppState, Any],
        timeout_seconds: Annotated[
            float | None,
            Field(gt=0, description="How long to wait at most (the server caps it)."),
        ] = None,
        return_when: Annotated[
            Literal["any", "all"],
            Field(description="`any`: return when one job ended; `all`: when every job ended."),
        ] = "any",
    ) -> dict[str, Any]:
        """Waits for jobs to end and returns when something worth acting on happened. Use this
        instead of calling get_job again and again.

        It polls the jobs and returns as soon as, with `return_when` any, one job ended (or, with
        all, every job ended); or every job still running waits for someone (nothing will happen
        until a person approves it or its files are retried); or the time is up (`timedOut`).
        It never waits longer than the server's limit, so call it again until you are satisfied.

        Returns each job's state (`currentState`, `approved`, `configFileStatus`, `terminal`,
        `waitingForAction`, and `failureCategory` and `failureMessage` after a failure), the ids
        that ended (`terminalJobIds`) and the ids that wait for someone (`waitingForActionJobIds`),
        and `next_steps`. A job that is not in your context answers "not found".
        """
        state = get_state(ctx)
        settings = state.settings
        unique_ids = list(dict.fromkeys(job_ids))
        if len(unique_ids) > settings.wait_max_job_ids:
            raise ToolError(
                f"Too many jobs: wait for at most {settings.wait_max_job_ids} at a time "
                f"(got {len(unique_ids)})."
            )
        timeout = min(
            timeout_seconds if timeout_seconds is not None else settings.wait_default_seconds,
            settings.wait_max_seconds,
        )
        return await wait_for(
            state.core,
            parse_context_uuid(context_uuid),
            unique_ids,
            timeout_seconds=timeout,
            return_when=return_when,
            poll_seconds=settings.wait_poll_seconds,
            max_poll_errors=settings.wait_max_poll_errors,
            on_poll=lambda elapsed, total, message: ctx.report_progress(elapsed, total, message),
        )

    @mcp.tool(annotations=CREATES)
    async def queue_job(
        context_uuid: ContextUuid, job_id: JobId, ctx: Context[AppState, Any]
    ) -> dict[str, Any]:
        """Queues a DRAFT job so it can run. It needs the job to be approved and its
        configFileStatus to be READY.

        If the job is not approved, a human must approve it first (you cannot): tell the user. If
        the files are PREPARING, wait and check get_job; if they FAILED, call
        retry_job_preparation. After queuing the job is TOQUEUE; the core's scheduler picks such
        jobs about every 60 seconds, then the worker runs them. Use wait_for_jobs to follow it.
        """
        state = get_state(ctx)
        with hints({CoreConflict: QUEUE_409}):
            return await job_service.queue_job(state.core, parse_context_uuid(context_uuid), job_id)

    @mcp.tool(annotations=IDEMPOTENT_WRITE)
    async def retry_job_preparation(
        context_uuid: ContextUuid, job_id: JobId, ctx: Context[AppState, Any]
    ) -> dict[str, Any]:
        """Builds the config files of a DRAFT job again. Use it only when get_job shows
        configFileStatus FAILED (or PREPARING for a long time).

        Returns once the core accepted the request: the files are built in the background, so
        check get_job until configFileStatus is READY, then call queue_job. If the cause was your
        custom_config, create a new job with a fixed one instead: an unchanged retry fails again.
        """
        state = get_state(ctx)
        with hints({CoreConflict: RETRY_409}):
            return await job_service.retry_preparation(
                state.core, parse_context_uuid(context_uuid), job_id
            )

    @mcp.tool(annotations=IDEMPOTENT_WRITE)
    async def set_job_ai_result(
        context_uuid: ContextUuid,
        job_id: JobId,
        ai_result: Annotated[
            str, Field(description="Your conclusion about the job, at most 5000 characters.")
        ],
        ctx: Context[AppState, Any],
    ) -> dict[str, Any]:
        """Records your conclusion about a job that has ended, so humans and later sessions can
        read it with the job.

        Write what was tested, what was observed, whether the hypothesis held, and the decision
        (finding, no finding, or retry with other parameters). Keep it short: the core refuses
        more than 5000 characters. The job must be in a terminal state; otherwise the core says
        it has not ended (use wait_for_jobs). Calling it again replaces the earlier text.
        """
        state = get_state(ctx)
        with hints({CoreConflict: AI_RESULT_409}):
            await state.core.set_job_ai_result(parse_context_uuid(context_uuid), job_id, ai_result)
        return {"jobId": job_id, "saved": True}

    @mcp.tool(annotations=DESTRUCTIVE)
    async def cancel_job(
        context_uuid: ContextUuid, job_id: JobId, ctx: Context[AppState, Any]
    ) -> dict[str, Any]:
        """Cancels a job of your context that you started and no longer need. This cannot be
        undone.

        A job can be canceled in TOQUEUE, QUEUED, RUNNING or PAUSED. A TOQUEUE job is canceled at
        once; for QUEUED, RUNNING and PAUSED jobs the cancel is a request (`requestedState`
        CANCELED) that the worker carries out the next time it polls, so check get_job. A PAUSED
        job may have been paused by a human; canceling it is final. A DRAFT cannot be canceled
        (just do not queue it) and an ended job is final.
        """
        state = get_state(ctx)
        with hints({CoreConflict: CANCEL_409}):
            return await job_service.cancel_job(
                state.core, parse_context_uuid(context_uuid), job_id
            )
