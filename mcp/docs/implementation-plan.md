# automata-mcp — Implementation Plan

Written on 2026-10-22 from the core repo (`/home/haidar/dev/automata/code/automata`, branch `staging`) and brought up to date on 2026-10-26 (core merge commit `a3e02b2`; worker `main` merge `afb686d`) after the core took over every business rule: scoped findings, readable requests of equality sets, wordlist path check, job creation rules, host and context rate limits, `terminal`/`waitingForAction` and `failureCategory` on jobs. It describes what to build; **no code exists yet**. A fresh session should be able to implement the whole server from this file plus `.claude/CLAUDE.md`.

Facts about the core below come from reading its code, not from memory. Anything marked **(verify)** is a fact about a third-party library or about behaviour that could not be confirmed from the core's code; check it against the installed version / a real call when implementing.

---

## 0. How to use this document

1. Read `.claude/CLAUDE.md` first (workflow rules: do not start coding until the user says so, no commit/merge without confirmation, branches from `staging`, branch names contain a verb, tests for every new function).
2. Implement phase by phase (section 14). Each phase is one or more branches created from `staging`.
3. The list of tools comes from the table `.claude/mcp-operations-access.md` **in the core repo**: one tool per row marked `t`. Section 17.A maps every `t` row to its tool. If the table changes, update sections 8 and 17.A first.
4. Section 3 lists decisions the user already took. Do not reopen them. Section 16 lists open choices with the default to use unless the user says otherwise.
5. The core is the backend; never work around a core limitation inside this repo. Section 12 lists known core gaps and the recommended core changes (made in the core repo, as separate work).

---

## 1. Goal and scope

**Goal.** An MCP server that lets an AI agent test the hosts of an Automata *agent context*: read the requests/responses/tenants/host that the context gives access to, add requests it observed, create/queue/watch/cancel automation jobs, read job results, and record findings.

**In scope**
- 37 MCP tools (section 8) over the core's REST API, one per `t` row of the operations table plus two aggregating tools (`get_context_briefing`, `wait_for_jobs`).
- One MCP prompt carrying the testing workflow, and a few read-only MCP resources (section 10).
- Soft guardrails that belong to the MCP layer: argument type validation and response-size caps (section 11); finding scoping is enforced by the core.
- Packaging: stdio (local clients) and streamable HTTP (Docker), Dockerfile, a compose service in the core repo.

**Not in scope** (do not build)
- Persistence of any kind, background jobs, scheduling (the core owns state).
- Authentication / API keys (the user decided: none).
- Any decision making or reasoning (the LLM decides).
- Running tools (the worker runs jobs).
- Operations the table marks `f`: pause/resume/approve/delete jobs, user job creation, any management of programs/hosts/tenants/contexts/routines/vulnerabilities, deleting anything, reviewing findings, Burp ingestion, worker endpoints, unscoped request/response/equality-set/tenant reads.
- Core features described in the core's `enhancement-plan.md` that do **not exist** in the core today: test plans, per-context budgets, SSE notifications, structured job-result summaries, finding-context endpoint, context briefing endpoint, audit log. The server must work without them (section 12 lists which of its behaviours stand in for them).

---

## 2. Architecture

```
┌──────────────────────────────┐
│ AI agent (Claude, ...)       │   reasons, decides
└──────────────┬───────────────┘
               │ MCP (stdio or streamable HTTP)
┌──────────────▼───────────────┐
│ automata-mcp  (this repo)    │   tools, prompts, resources, caps,
│  Python, official mcp SDK    │   polling, error mapping
└──────────────┬───────────────┘
               │ REST (httpx), no auth
┌──────────────▼───────────────┐         ┌───────────────┐
│ Automata core (Spring Boot)  │────────▶│ RabbitMQ / S3 │──▶ worker (Go) ──▶ ffuf, masser
│ data, rules, scheduling      │         └───────────────┘
└──────────────────────────────┘
```

| Layer | Owns | Does not own |
|---|---|---|
| LLM | reasoning, decisions | state, execution |
| automata-mcp | tool surface, argument types only, text/size handling, polling, error translation, size caps | persistence, business rules, scheduling, approval |
| Core | data, business rules, approval gate, rate caps, job state machine, scoping of reads | reasoning, tool dispatch |

One server process serves one agent connection (stdio) or several (HTTP); it is stateless (no counters, no caches that matter). Restarting it loses nothing.

**How scoping works.** The core has no authentication. The agent can only reach what the *context-scoped* routes expose: every route under `/api/agent-contexts/uuid/{uuid}/...` checks that the request/job belongs to the context and answers **404** otherwise (a request outside the context looks exactly like a missing one). The server must call only those routes for requests, responses, tenants, host and jobs. The routes that are not context-scoped (routines, vulnerabilities, wordlists, findings, export, request ingestion) are limited by the MCP layer as described in sections 9 and 11.

---

## 3. Decisions already taken (do not reopen)

1. **Language/SDK:** Python, official `mcp` SDK, high-level server class. In `mcp` 2.x (the version in use, 2.3.0 when the project was set up) `FastMCP` was renamed `MCPServer`: `from mcp.server.mcpserver import MCPServer`; `mcp.server.fastmcp` no longer exists. Not the standalone `fastmcp` package. Separate repo at `/home/haidar/dev/automata-mcp`. (Wherever this plan says `FastMCP`, read `MCPServer`; the 2.x API differs from 1.x in other places too, so every **(verify)** about the SDK is checked against 2.x, e.g. the in-memory test helper `create_connected_server_and_client_session` is not in `mcp.shared.memory` any more; see section 13.)
2. **No authentication**, in the core or in this server.
3. **What the agent may do** is exactly the `t` rows of the operations table (section 17.A). The unscoped read routes of requests, responses, equality sets, tenants, hosts and jobs are `f`: the agent reads them only through the scoped routes.
4. **"In the context" rule** (enforced by the core): a request is in the context when a `REQUEST` item is it, or it is in an `EQUALITY_SET` item, **every request of the set under both strategies**. The strategy only decides the *resolved targets* (`list_targets`; under `LATEST` the newest request of each set, under `AI_JUDGEMENT` every request), which are the only requests a job can target. Older members of a set are readable but not job targets. Anything else → 404.
5. **Tenants:** a context has an *explicit* list of tenants (all of the context's host). The agent may read their static authentication data (headers/cookies/query parameters) through `GET .../tenants`, together with the ids of the in-context requests tied to each tenant. Dynamic authentication was removed from the core.
6. **Jobs always run as the target request's tenant.** The agent cannot choose another tenant for a job.
7. **Job operations of the agent:** create (narrow, single request, target must be in the context), list, get, get result (inline), queue, retry preparation, set AI result, cancel. Not: pause, resume, approve, delete, user-path creation. Unapproved AI jobs cannot be queued (409): a human approves them. **History:** the agent can also read (never change) the jobs that ever targeted an in-context request, including those of other contexts and of users (`list_request_jobs`, `get_request_job`, `get_request_job_result`); they show an `origin`, never another context's identity.
8. **Ingestion:** the agent may add a request (and optional response) for **any host by name** through `POST /api/external/ai/requests` (stored with `Source.AI`). It is **not** added to any context; the agent cannot read it back or create jobs on it until a human adds it to a context. `tenantId` is optional and needs `agentContextUuid`: the tenant must be in that context's tenant list and belong to the request's host.
9. **Findings** go through the context-scoped routes of the core (rows 119–122, done in the core): a finding is visible when its request is readable in the context (a request item or any member of an equality set item, under both strategies); it may point only to an AI job of this context; the core sets `createdBy=AI`; only a finding created by this context's agent and not reviewed can be edited. **Wordlist creation and body-property export are `t`** (the user's choice) although their core routes are not context-scoped: a wordlist is a file the core stores itself (an object in its `wordlists` bucket, never changed afterwards, at most 512 MB, a file that is already a wordlist is refused), so the agent uploads its text and cannot point the core at a path (C2, done); the export is pinned to the context's host by the MCP on purpose (C3, intentionally left as is).

10. **The server holds no business logic.** What stays in the server is the shape of its own arguments and its own resource limits, not domain rules: the one-of rules of `add_request` (the server must pick which input to encode), the limits of `wait_for_jobs` (`AUTOMATA_WAIT_MAX_JOB_IDS`, `AUTOMATA_WAIT_MAX_POLL_ERRORS`), the UUID format of `context_uuid`, and the size caps. Every rule (validation, scoping, state meanings, failure classification, limits) is enforced by the core, and its message is shown. The server only translates (errors, encodings, base64/CRLF, `rawRequestText`), caps sizes, pages, polls and describes the tools. A missing rule is a core change (with a row in the operations table), never a check here.
11. **Dropped on purpose:** the loop detector for identical jobs (section 11.1), the finding scope checks, the wordlist path guard, the client-side validations of the rules of `create_job`, `set_job_ai_result` and `add_request`, and the regex failure analysis (replaced by the core's `failureCategory`, section 17.C).

---

## 4. Stack and layout

**Runtime:** Python ≥ 3.12.

**Dependencies** (pin the then-current versions when setting up the project; check that the installed `mcp` has `MCPServer`, structured tool output, `Context.report_progress`, tool annotations, and streamable HTTP — **verify**):

| Package | Use |
|---|---|
| `mcp` | official SDK, `MCPServer` (2.x; formerly `FastMCP`) |
| `httpx` | async REST client (streaming for bodies/results) |
| `pydantic` (v2), `pydantic-settings` | models for core payloads and tool I/O; env configuration |
| dev: `pytest`, `pytest-asyncio`, `respx` (mock httpx), `pytest-cov`, `ruff`, `mypy` | tests and quality |

**Tooling:** `uv` + `pyproject.toml` (committed `uv.lock`), `ruff` (lint+format), `mypy --strict` on `src/`, entry point script `automata-mcp`.

*As set up in phase 0:* build backend `uv_build`; `uv` was not installed and was put in place with `pipx install uv`. ruff selects `E, F, I, B, UP, SIM, ASYNC, RUF` with line length 100, and ignores `RUF012` under `core/models/` (pydantic copies mutable defaults for every instance, so `= []` is safe there). mypy runs `strict` on `src` with the pydantic plugin. pytest runs with `asyncio_mode = "auto"`, the marker `integration` and branch coverage configured (`uv run pytest --cov`). The `Makefile` has `sync`, `lint`, `format`, `format-check`, `typecheck`, `test` and `check` (`make check` runs the four gates). `.claude/` is not tracked in git (the user's decision). The console script `automata-mcp` was added to `pyproject.toml` in phase 2.

**Layout**
```
automata-mcp/
  .claude/CLAUDE.md
  docs/implementation-plan.md          (this file)
  docs/core-contract.md                (phase 1: routes used + payload samples, kept in line with the core)
  pyproject.toml  uv.lock  README.md  .env.example  Dockerfile  .dockerignore  .gitignore
  scripts/check_core_contract.py       (phase 9: compares used routes with the core's OpenAPI)
  src/automata_mcp/
    __init__.py  __main__.py           (main(): load settings, configure logging, build and run the server; `python -m automata_mcp`)
    config.py                          Settings (pydantic-settings, env prefix AUTOMATA_)
    server.py                          builds the MCPServer (create_server), lifespan, startup check of the core, run_server (transports)
    state.py                           AppState: CoreClient, settings (created in the lifespan); get_state(ctx) for tools
    logging_setup.py                   configure_logging (stderr) and the log_tool_calls middleware
    core/
      client.py                        CoreClient: one async method per route used (section 6.2)
      errors.py                        CoreError hierarchy + status→message mapping
      paging.py                        Page[T] model, size/page clamping
      models/                          pydantic models (section 7): context.py, requests.py, responses.py, jobs.py,
                                       findings.py, reference.py (routine/vulnerability/wordlist), tenants.py,
                                       enums.py (the enums of section 6.3, tolerant of unknown values)
    services/
      bodies.py                        stream a body with a byte cap, decode text, binary detection
      raw_messages.py                  normalize/encode raw HTTP text <-> base64, compose message
      waiting.py                       wait_for_jobs polling logic
      config_validation.py             rawRequestText conversion (no validation: the core validates)
      briefing.py                      builds get_context_briefing
    tools/
      context.py  requests.py  responses.py  jobs.py  reference.py  wordlists.py  findings.py  ingestion.py  export.py
    prompts.py  resources.py
  tests/
    unit/...  fixtures/core/*.json  integration/ (opt-in, real core)
```

Tool modules only declare tools (`@mcp.tool`) and delegate to `services/` and `core/`. No HTTP code inside tools.

---

## 5. Configuration

`config.py` — `Settings(BaseSettings)`, env prefix `AUTOMATA_`, `.env` optional.

*Validation as implemented:* `core_url` must start with `http://` or `https://` and loses its trailing slashes; `transport` is `stdio` or `streamable-http`; `log_level` is one of the standard level names; `http_port` is 1–65535; sizes, durations and limits are greater than 0 (`get_retries` and `target_enrich_limit` may be 0); `wait_default_seconds` must not exceed `wait_max_seconds`; `body_max_bytes` must not exceed `body_max_fetch_bytes`. Tests build it with `Settings(_env_file=None)` so a local `.env` never leaks in.

| Variable | Default | Meaning |
|---|---|---|
| `AUTOMATA_CORE_URL` | `http://localhost:8080` | base URL of the core (compose publishes it on 8080) |
| `AUTOMATA_TRANSPORT` | `stdio` | `stdio` or `streamable-http` |
| `AUTOMATA_HTTP_HOST` / `AUTOMATA_HTTP_PORT` | `0.0.0.0` / `8000` | for `streamable-http`; passed to `run_streamable_http_async(host=, port=)` (the SDK's own default host is `127.0.0.1`). The SDK has `TransportSecuritySettings` (Host-header check), **verified in phase 2** (`tests/unit/test_http_transport.py`): when the server is bound to `0.0.0.0` the check is off and a request addressed to the compose service name (`automata-mcp:8000`) is accepted (200); only a bind to `127.0.0.1`/`localhost` turns it on, and then a foreign Host answers 421. So nothing needs configuring for compose |
| `AUTOMATA_HTTP_MAX_REQUEST_BYTES` | `4194304` | for `streamable-http`; the SDK's `max_request_body_size` (4 MB default) caps one tool call, so it caps `create_wordlist` content; raise it here if bigger wordlists are needed (stdio has no such limit) |
| `AUTOMATA_REQUEST_TIMEOUT_SECONDS` | `30` | httpx read timeout (connect timeout fixed at 5) |
| `AUTOMATA_GET_RETRIES` | `2` | retries of GETs on connect errors/5xx/timeouts (backoff 0.5 s, 1 s). Never retry POST/PATCH |
| `AUTOMATA_BODY_MAX_BYTES` | `65536` | default cap of text returned for a request/response body |
| `AUTOMATA_BODY_MAX_FETCH_BYTES` | `5242880` | hard cap of bytes read from a stream (including skipped offset) |
| `AUTOMATA_RESULT_MAX_BYTES` | `100000` | default cap of a job result returned in one call |
| `AUTOMATA_WAIT_POLL_SECONDS` | `5` | poll interval of `wait_for_jobs` |
| `AUTOMATA_WAIT_DEFAULT_SECONDS` / `AUTOMATA_WAIT_MAX_SECONDS` | `45` / `55` | default/max wait of one call (must stay under the MCP client's tool timeout — **verify** the client's limit) |
| `AUTOMATA_WAIT_MAX_JOB_IDS` | `20` | most job ids one `wait_for_jobs` call accepts (bounds the parallel polls) |
| `AUTOMATA_WAIT_MAX_POLL_ERRORS` | `3` | consecutive transient poll errors after which `wait_for_jobs` fails |
| `AUTOMATA_TARGET_ENRICH_LIMIT` | `50` | max targets enriched with request metadata in `list_targets` |
| `AUTOMATA_LOG_LEVEL` | `INFO` | |

`.env.example` documents all of them. Never log values of secrets or tool results (tenant authentication is returned by `list_tenants`).

---

## 6. The core API contract

### 6.1 Conventions

- Base URL = `AUTOMATA_CORE_URL`. JSON in/out unless noted. No authentication, no versioning.
- **Error bodies are plain text** (the exception message), not JSON. Status mapping (core `GlobalExceptionHandler`): `404` entity not found; `400` illegal argument / unreadable body (bad JSON, unknown enum name); `409` invalid job state, job not approved, a finding that cannot be edited (not created by this context's agent, or reviewed by a human), optimistic-lock conflict; `422` ineligible job target (a body that is not empty/JSON/FORM, another host, or a host with a rate limit of 0); `501` unsupported operation; `413` a wordlist file over 512 MB (`create_wordlist` only); `415` unsupported media type (shown as a generic refusal); `500` anything else.
- **Paged lists** return `{"content": [...], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0, "last": true}`. Query params `page` (0-based), `size`, `sort=prop,asc|desc`. The tools expose `page` and `size` (clamp `size` to ≤ 100) and no `sort`.
- Creation routes answer `201` with a `Location` header and usually an empty body.
- Dates: `LocalDate` as `YYYY-MM-DD`; instants as ISO-8601 strings.
- A repeated/stale update can answer `409` "changed at the same time": tell the agent to re-read and retry.

### 6.2 Routes used (one client method each)

`{uuid}` is the context UUID, `{rid}` a request id, `{jid}` a job id.

| Tool | Method + path | Query / body | Success |
|---|---|---|---|
| get_context | `GET /api/agent-contexts/uuid/{uuid}` | | 200 `AgentContextDetailedResponse` |
| list_targets | `GET /api/agent-contexts/uuid/{uuid}/resolved-requests` | | 200 `ResolvedTarget[]` |
| get_host | `GET /api/agent-contexts/uuid/{uuid}/host` | | 200 `HostDetailedResponse` |
| list_tenants | `GET /api/agent-contexts/uuid/{uuid}/tenants` | | 200 `AgentContextTenantResponse[]` |
| list_context_requests | `GET /api/agent-contexts/uuid/{uuid}/requests` | `equalitySetId`, `page`, `size` (≤ 100, no `sort`) | 200 page of `ContextRequestSummary` (every request item and every member of an equality set item, newest first); 404 when `equalitySetId` is not an item of the context |
| get_request | `GET .../uuid/{uuid}/requests/{rid}/metadata` | | 200 `RequestMetadataResponse` |
| get_request_raw | `GET .../requests/{rid}/raw` | | 200 `RequestRawResponse` |
| get_request_body | `GET .../requests/{rid}/body/stream` | | 200 bytes, `Content-Type` = original, `Content-Disposition: attachment`; 404 when the request has no body |
| get_response | `GET .../requests/{rid}/response/metadata` | | 200 `ResponseMetadataDto`; 404 when the request has no response |
| get_response_raw | `GET .../requests/{rid}/response/raw` | | 200 `RawResponseDto` |
| get_response_body | `GET .../requests/{rid}/response/body/stream` | | 200 bytes |
| create_job | `POST /api/agent-contexts/uuid/{uuid}/jobs` | body `AiJobCreationRequest` | 201, `Location: /api/agent-contexts/uuid/{uuid}/jobs/{jid}`, empty body |
| list_jobs | `GET .../uuid/{uuid}/jobs` | `states` (comma list of JobState), `page`, `size` | 200 page of `HttpJobSummaryResponseDto` (always only this context's AI jobs, newest first) |
| get_job | `GET .../jobs/{jid}` | | 200 `HttpJobResponseDto` |
| get_job_result | `GET .../jobs/{jid}/result` | | 200 bytes with the routine's content type (`application/json` or `text/plain`); 409 if the job has not ended; 404 if no result |
| queue_job | `POST .../jobs/{jid}/queue` | | 204 |
| retry_job_preparation | `POST .../jobs/{jid}/retry-preparation` | | 202 |
| set_job_ai_result | `POST .../jobs/{jid}/ai-result` | body `{"aiResult": "..."}` | 204 |
| cancel_job | `POST .../jobs/{jid}/cancel` | | 204 |
| list_request_jobs | `GET .../uuid/{uuid}/requests/{rid}/jobs` | `states`, `page`, `size` | 200 page of `RequestJobSummaryResponseDto` (every narrow job that targeted the request, of any creator or context, newest first); 404 when the request is not in the context |
| get_request_job | `GET .../requests/{rid}/jobs/{jid}` | | 200 `RequestJobResponseDto`; 404 when the request is not in the context or the job did not target it |
| get_request_job_result | `GET .../requests/{rid}/jobs/{jid}/result` | | 200 bytes, same rules as `get_job_result`; 404 as above |
| list_routines | `GET /api/routines` | `query`, `vulnId`, paging (default size 30, sort key) | 200 page of `RoutineSummaryResponseDto` |
| get_routine | `GET /api/routines/{id}` | | 200 `RoutineDetailedResponseDto` (includes `configSchema`) |
| list_vulnerabilities | `GET /api/vulnerabilities` | `query`, `parentVulnId`, paging | 200 page of `VulnerabilitySummaryResponseDto` |
| get_vulnerability | `GET /api/vulnerabilities/{id}` | | 200 `VulnerabilityDetailedResponseDto` |
| list_wordlists | `GET /api/wordlists` | `query`, `vulnId`, paging | 200 page of `WordlistSummaryResponseDto` |
| get_wordlist | `GET /api/wordlists/{id}` | | 200 `WordlistDetailedResponseDto` |
| create_wordlist | `POST /api/wordlists` | multipart: part `file` (the text, any file name) and optional JSON part `metadata` `WordlistCreationRequest` | 201, `Location: /api/wordlists/{id}`; 409 when the same file is already a wordlist; 413 over 512 MB; 400 for an empty file, a file with a NUL byte, or too long name/description |
| create_finding | `POST /api/agent-contexts/uuid/{uuid}/findings` | body `AiFindingCreationRequest` | 201, `Location: /api/agent-contexts/uuid/{uuid}/findings/{id}`, empty body; 404 when the request is not readable in the context or `jobId` is not an AI job of the context that targeted it; 400 invalid fields |
| get_finding | `GET /api/agent-contexts/uuid/{uuid}/findings/{id}` | | 200 `AgentFindingDetailedResponse`; 404 when its request is not readable in the context |
| list_findings | `GET /api/agent-contexts/uuid/{uuid}/findings` | `requestId`, `jobId`, `status`, `humanVerdict`, `createdBy`, `page`, `size` (≤ 100) | 200 page of `AgentFindingSummaryResponse` (the findings of every readable request, newest first) |
| update_finding | `PATCH /api/agent-contexts/uuid/{uuid}/findings/{id}` | body `FindingPatchRequest` | 204; 404 when not visible; 409 when the finding was not created by this context's agent or a human reviewed it |
| export_body_property_names | `GET /api/export/body-properties` | `hostId` (exactly one of `hostId`/`programId` is required; always use `hostId`) | 200 `text/plain`, one name per line, `Content-Disposition: attachment` |
| add_request | `POST /api/external/ai/requests` | body `AiRequestAdditionDto` | 201 `RequestSummaryResponse` (no `Location`) |

Notes
- The paths under `.../requests/{rid}/...` are all relative to `/api/agent-contexts/uuid/{uuid}`.
- `GET /api/agent-contexts/uuid/{uuid}/jobs?states=FINISHED,FAILED` — the core binds a comma-separated list to `Set<JobState>`; repeated `states=` params also work (**verify**).
- Streaming routes answer `attachment`; ignore the disposition, read the bytes.

### 6.3 Enums (exact names)

| Enum | Values |
|---|---|
| JobState | `DRAFT, TOQUEUE, SCHEDULED, QUEUED, RUNNING, PAUSED, CANCELED, FINISHED, FAILED, SELFTERMINATED, LOSTCONTACT` |
| Terminal job states (information only: the core returns `terminal`, the server never uses this list) | `FINISHED, FAILED, SELFTERMINATED, CANCELED, LOSTCONTACT` |
| FailureCategory | `PREPARATION, POLL, INVALID_CONFIG, TOOL_START, TOOL_EXIT, RESULT, UNKNOWN` |
| JobOrigin / FindingOrigin | `THIS_CONTEXT, OTHER_CONTEXT, USER` |
| ConfigFileStatus | `PREPARING, READY, FAILED` |
| ResultsVerbosity | `SUMMARIZED, NORMAL, VERBOSE` |
| Duration | `SHORT, LONG` |
| HttpJobScope | `NARROW, WIDE` |
| JobCreator | `USER, AI` |
| RepresentativeStrategy | `LATEST, AI_JUDGEMENT` |
| AgentContextItemType | `REQUEST, EQUALITY_SET` |
| RequestContentType (mime family) | `JSON, FORM, MULTIPART, XML, HTML, JS, CSS, TEXT, IMAGE, AUDIO, VIDEO, PDF, ARCHIVE, OCTET_STREAM, OTHER` |
| ResponseContentType | same without `FORM, MULTIPART` |
| Method | `GET, POST, PUT, PATCH, DELETE, HEAD` |
| Source | `MANUAL, BURP, WAYBACK, AI` |
| BodyStorage | `NONE, INLINE, FILE` |
| FindingStatus | `SUGGESTED, CONFIRMED` |
| FindingCreator | `AI, USER` |
| HumanVerdict | `TRUE_POSITIVE, FALSE_POSITIVE` |
| ResultContentType | `JSON, TEXT` |
| Overhead / Protocol (routine) | `HIGH, MEDIUM, LOW` / `NETWORK, HTTP` |
| TargetCardinalityPair (routine) | `SINGLE_HOST, MULTILPLE_HOSTS` (sic, a typo in the core), `SINGLE_REQUEST, MULTIPLE_REQUESTS` |
| Host Scope | `WILDCARD, FQDN` |
| SelectorType | `SINGLE_HOST, SINGLE_REQUEST, MULTIPLE_HOSTS, MULTIPLE_REQUESTS` |

Models must tolerate unknown enum values and extra fields (the core evolves): use `extra="ignore"` and `str`-backed enums with an `UNKNOWN` fallback, or plain `str` for fields the server never branches on.

*As implemented (`core/models/enums.py`):* every enum derives from `CoreEnum` (a `StrEnum`). A value the enum does not know does not fail: it gives a member that keeps the raw value, whose `name` is `UNKNOWN`, that compares equal to the raw string and is sent back unchanged (so nothing is lost when a payload is shown or re-sent). A value that is not a string is still rejected. `JobOrigin` and `FindingOrigin` are one enum, `Origin`. Dates and instants are kept as `str`. Fields the server never branches on stay plain `str` (`BodyMetadata.mime_family`, the fields of `MatchAndReplace`).

---

### 6.4 The client as implemented (phase 1, `core/client.py`)

- `CoreClient(base_url, *, timeout_seconds, get_retries, transport=None, sleep=asyncio.sleep)`; `CoreClient.from_settings(settings)`; an async context manager (`aclose()` on exit). `transport` and `sleep` exist so tests can inject a mock transport and a fake clock. The context uuid is a `uuid.UUID` (the tools parse the string first, section 9.1).
- One method per route of 6.2, named as in `docs/core-contract.md`. Lists return `list[Model]`, pages `Page[Model]`, creations the new id (read from the last segment of `Location`), actions `None`.
- **Retries:** only GETs, only on connect errors, timeouts and 5xx, `get_retries` times, with a backoff of 0.5 s, 1 s, ... Any other transport error (for example a dropped connection) is not retried and becomes "core unreachable". A write that could not connect says in its message that it may or may not have been applied.
- **Streams:** `stream_request_body`, `stream_response_body`, `stream_job_result` and `stream_request_job_result` are async context managers yielding the `httpx.Response`: `async with client.stream_job_result(uuid, jid) as response:`. The status is checked before the body is reachable (a 4xx/5xx raises the mapped error), and the response is always closed. The caller (phase 4's `services/bodies.py`) reads at most what it needs.
- **Unreadable answers** (a body that is not JSON, a payload of the wrong shape, a list route answering an object, a page route answering a list, a creation without `Location`) raise `CoreResponseError`; the server never passes through half-parsed data.
- **Bodies sent** are the `CoreRequest` models; `to_payload()` gives camelCase keys, enums as strings and **leaves out every field that was not given** (never a `null`); `wordlistsIds` is always sent, also when empty. `states` is sent as one comma-separated value; unset filters are not sent. `create_wordlist(content, *, file_name, metadata=None)` sends the multipart parts `file` (any text) and, when given, `metadata` as JSON without a file name. `export_body_property_names(host_id)` sends only `hostId` and returns the non-blank lines.
- **Paging:** `page_params(page, size)` clamps `page >= 0` and `1 <= size <= 100`; unset query values are dropped (`drop_none`) but an empty `query=""` is sent.

## 7. Data models (pydantic, camelCase aliases)

Base config: `ConfigDict(extra="ignore", populate_by_name=True, alias_generator=to_camel)`. Field names below are the JSON names.

**Paging:** `Page[T]{content: list[T], page, size, totalElements, totalPages, last}`.

**Agent context**
- `AgentContextDetailedResponse{id, uuid, name, description?, hostId, defaultAutoApproval: bool, representativeStrategy, allowedRoutineIds: list[int], tenantIds: list[int], defaultDuration?, defaultVerbosity?, defaultPriority?, maxRate, creationDate, items: list[{id, itemType, requestId?, equalitySetId?}]}`
- `ResolvedTarget{requestId, automataJobEligible: bool, autoApproval: bool, sourceItemType, sourceEqualitySetId?}`
  - `automataJobEligible=false` means the request cannot be a job target (its body is neither empty nor JSON/FORM, e.g. multipart): test it by other means.
  - `autoApproval=true` means a job on it needs no human approval; `false` means a human must approve the draft.
- `HostDetailedResponse{id, insertionDate, host, level, scope, outOfScope, hostRateLimit, shortRateLimit, longRateLimit, programId, description?, technologies?: list[str], notes?}` (a rate limit of 0 means no job can run on the host: the core refuses to create one, 422).
- `AgentContextTenantResponse{tenantId, name, email, authData?: StaticAuthData, requestIds: list[int]}`; `StaticAuthData{data: {headers, cookies, queryParameters: list[{key, value}]}, lifeTimeInSeconds?, populatedAt?}` (both are instants, ISO timestamps, despite the first name) (`authData` is null when the tenant has no authentication).

**Requests / responses**
- `ContextRequestSummary{requestId, method, computatedPath, equalitySetId?, tenantId?, resolvedTarget: bool, automataJobEligible: bool, insertionDate}` (`equalitySetId` is the context's set item that holds the request, null for a plain request item; `resolvedTarget` says whether the request is also in `list_targets`, i.e. a job can target it).
- `RequestMetadataResponse{requestId, hostId, programId, tenantId?, responseId?, source, insertionDate, line: {method, computatedPath, extension?, version, target}, headers: list[{name, value}], body: BodyMetadata}`
  - `computatedPath` is the path with variable segments abstracted; `target` is the request target exactly as sent (path + query string).
- `ResponseMetadataDto{responseId, requestId, hostId, statusCode, insertionDate, headers: list[{name, value}], body: BodyMetadata}`
- `BodyMetadata{storage: NONE|INLINE|FILE, contentType?, mimeFamily?, sizeBytes}` (`contentType` is the Content-Type header as received; `mimeFamily` is the enum name).
- `RequestRawResponse{rawHeadBase64, rawBodyBase64?, bodyStoredAsFile, responseId?}`; `RawResponseDto{rawHeadBase64, rawBodyBase64?, bodyStoredAsFile}`. `rawBodyBase64` is null when there is no body **or** the body is a file; use `body.storage` from the metadata (or `bodyStoredAsFile`) to tell. The head is the start line + headers + the blank line; it decodes as ISO-8859-1.
- `RequestSummaryResponse{requestId, method, computatedPath, extension?, version, numberOfProperties, contentType?, source, hostId, tenantId?, programId, responseId?, bodyStoredAsFile, bodySizeBytes}` (answer of `add_request`).

**Jobs**
- **`terminal` and `waitingForAction`** (on all four job DTOs below, computed by the core): `terminal` is true when the job ended and will not change (`FINISHED`, `FAILED`, `SELFTERMINATED`, `CANCELED`, `LOSTCONTACT`). `waitingForAction` is true for a `DRAFT` job that does not move on by itself: an AI job nobody approved yet (a human must approve it) or a job whose config files failed (`configFileStatus=FAILED`, call `retry_job_preparation`). A draft that is approved with READY files is not waiting: the agent still has to `queue_job` it.
- `HttpJobSummaryResponseDto{id, creationDate, scope, currentState, priority, rate, routineId, routineKey, createdBy, approved, terminal, waitingForAction}`
- `RequestJobSummaryResponseDto{id, creationDate, scope, currentState, priority, rate, routineKey, approved, terminal, waitingForAction, origin}` and `RequestJobResponseDto{jobId, currentState, requestedState?, verbosity, priority, rate, customConfig(json)?, routineKey, failureMessage?, failureCategory?, aiDescription?, aiResult?, approved, terminal, waitingForAction, origin}` (job history of a request). `origin` is `THIS_CONTEXT | OTHER_CONTEXT | USER`; these DTOs carry no context id/uuid, no program, no target selector and no `genericConfig`/`configFileStatus`. Wide jobs are never listed (no per-request link).
- `HttpJobResponseDto{jobId, httpJobScope, currentState, requestedState?, verbosity, priority, rate, targetSelector{selectorType, selector(json)}, genericConfig{matchAndReplace: list[{targetType (HEADER), rule (ADD|REPLACE|DELETE), targetKey, value}]}, customConfig(json)?, program{id, name}, routine{id, key}, failureMessage?, failureCategory?, configFileStatus, configFileFailure?, aiDescription?, aiResult?, createdBy, approved, terminal, waitingForAction, agentContextUuid?}`
- `AiJobCreationRequest` (body of `create_job`): `{requestId, aiDescription, routineId, duration?, wordlistsIds: list[int] (required, may be empty), rate?, verbosity?, priority?, customConfig?}` — note the field is `wordlistsIds`. The context uuid is in the path.

**Reference data**
- `RoutineSummaryResponseDto{id, creationDate, key, isAvailableAtConsumer, wordlistRequired, overhead, protocol, allowedTargets: list[TargetCardinalityPair]}`
- `RoutineDetailedResponseDto{id, creationDate, key, description?, updatedAt, isAvailableAtConsumer, wordlistRequired, overhead, protocol, allowedTargets, vulnerabilityId?, configSchema?(json), resultContentType}`
- `VulnerabilitySummaryResponseDto{id, name}`; `VulnerabilityDetailedResponseDto{id, name, parentId?}`
- `WordlistSummaryResponseDto{id, name?, description?, numberOfLines, sizeBytes}`; `WordlistDetailedResponseDto{id, name?, description?, numberOfLines, sizeBytes, sha256}` (no path any more); `WordlistCreationRequest{name?, description?, vulnIds?: list[int]}` (the `metadata` part; `name` falls back to the file name)

**Findings**
- `AiFindingCreationRequest{requestId, jobId?, vulnerabilityId, severity: float 0-10, status, confidence: float 0-10, evidence, pocReproductionSteps?, aiDescription?}` — there is no `createdBy`: the core sets `AI` and links the finding to the context. `pocReproductionSteps` is required (non-blank) when `status = CONFIRMED`. The core validates every field; the server does not repeat it.
- `AgentFindingSummaryResponse{id, requestId, hostId, vulnerabilityId, severity, status, confidence, createdBy, origin, humanVerdict?, creationDate}`; `origin` is `THIS_CONTEXT | OTHER_CONTEXT | USER` and no context id or UUID is ever returned.
- `AgentFindingDetailedResponse{id, creationDate, requestId, hostId, jobId?, vulnerabilityId, severity, status, confidence, evidence, pocReproductionSteps?, createdBy, origin, aiDescription?, humanVerdict?, humanComment?, reviewedAt?}`. A finding can be on a request the agent can read only as part of an equality set or not at all (`get_request` may answer 404 for an older member of a set under a context that no longer lists it): the finding is still shown.
- `FindingPatchRequest`: send only the fields to change: `{severity?, status?, confidence?, evidence?, pocReproductionSteps?, aiDescription?}`; to clear: `setPocReproductionStepsNull: true` / `setAiDescriptionNull: true`. The core calls `.ifPresent` on each `Optional`; an absent field must stay absent, never `null` (**verify** with a real PATCH that omitting a field is fine).

**Ingestion**
- `AiRequestAdditionDto{requestBase64, host (the host NAME), base64Response?, tenantId?, agentContextUuid?}` — base64 of the raw HTTP message bytes (request line, headers, blank line, body). Rules (core): unknown host → 404; `tenantId` without `agentContextUuid` → 400; unknown context → 404; tenant not in the context → 400; tenant of another host than the request's → 400; unparseable request/response → 400.

---

## 8. Tool catalog

### 8.1 Conventions

- Names are snake_case verbs. Every tool that touches a context takes a required `context_uuid: str` as its first parameter (validated as a UUID; there is no default context and no env fallback). All ids are ints.
- Return structured data (dicts/pydantic) — **not** pre-formatted prose — plus, where useful, a short `next_steps: list[str]` of hints. Keep outputs compact: drop null fields, never return base64 blobs the agent cannot read (decode text; summarise binary).
- **The docstring of each tool is the prompt.** For every tool write: what it returns, *when to use it*, what to do with the result, and the limits (what it will refuse). Use the description guidance given below as the minimum.
- Annotations (**verify** the SDK API `ToolAnnotations`): `readOnlyHint=True` for every GET tool; `destructiveHint=True` + `idempotentHint=False` for `cancel_job`; `idempotentHint=False` for creating tools and for `queue_job` (a second call after the job moved on answers 409); `idempotentHint=True` for `retry_job_preparation`, `set_job_ai_result` and `update_finding` (repeating them leaves the same state), all non-destructive; `openWorldHint=False` for all. These hints only advise the client: the core enforces the real rules.
- Errors: raise an exception whose message is guidance text; never return a stack trace. **Verified against `mcp` 2.x (phase 2):** only a `ToolError` (`mcp.server.mcpserver.exceptions`) reaches the agent with its message (as an `isError` result reading `Error executing tool <name>: <message>`, logged at INFO). Any other exception is a crash: the agent sees only `Error executing tool <name>` and the SDK logs the traceback at ERROR. So `CoreError` is a `ToolError` (every core failure carries its guidance without a try/except in the tools), and the other failures a tool anticipates (a `context_uuid` that is not a UUID, the one-of rules of `add_request`) must raise `ToolError` too. Mapping in section 9.5.
- Every tool call is logged at INFO: tool name, context uuid, duration, outcome. No payloads. *Implemented (`logging_setup.log_tool_calls`) as server middleware*, so no tool does it: one line `tool=<name> context=<uuid|None> outcome=ok|error duration_ms=<n>` on the logger `automata_mcp.tools`; the arguments and results are never logged. Logs go to stderr (stdout is the MCP channel under stdio): `configure_logging(level)` sets the level of the `automata_mcp` loggers and WARNING for the libraries, and can be called twice.
- Tools live in `tools/<group>.py` and each exposes `register(mcp)`, which declares its tools with `@mcp.tool`; `tools/__init__.py:register_all` is the one list of the modules, so adding a group adds one line there. A tool reaches the core through `get_state(ctx).core` (`ctx: Context[AppState, Any]` as a parameter; the SDK does not put it in the schema).

### 8.2 Context tools (`tools/context.py`)

**`get_context_briefing(context_uuid)`** — aggregate; start here.
- Calls: get_context, get_host, list_targets, list_tenants, get_routine for each `allowedRoutineIds`, list_jobs (page 0, size 10), list_findings (size 5). Run concurrently with `asyncio.gather`; a failing optional part (findings, jobs) is reported as `{"error": "..."}` in its slot, not as a total failure.
- Returns: `{context{uuid, name, description, representativeStrategy, defaultAutoApproval, defaults{duration, verbosity, priority}, maxRate}, host{...}, targets[...], tenants[...], allowedRoutines[{id, key, description, resultContentType, configSchema, wordlistRequired, allowedTargets}], recentJobs[...], recentFindings{total, items[...]}, notes[...]}`.
- `notes` explains the situation: how many targets are not job-eligible, which need human approval, that tenants' authentication is sensitive, the host rate limit (a limit of 0 closes the host to jobs), the context `maxRate`, which allowed routines have `wordlistRequired`, etc.
- Description guidance: "Call this first in a session. It gives your targets, tenants, host, allowed routines and recent work in one call."

**`get_context(context_uuid)`** — row 81. Returns the detailed context (including `items`, `tenantIds`, `allowedRoutineIds`, defaults). Guidance: use when you only need the configuration.

**`get_host(context_uuid)`** — row 90. Returns host details (description, technologies, notes, rate limits, scope, `outOfScope`). Guidance: use to understand the target's stack and its rate limits before choosing a rate.

**`list_targets(context_uuid, with_summary=true)`** — row 89.
- Returns the resolved targets. With `with_summary` (default true) enrich each target (up to `AUTOMATA_TARGET_ENRICH_LIMIT`, concurrency ≤ 8) with `method`, `computatedPath`, `target`, `contentType`, `tenantId`, `responseId`, `bodySizeBytes` from `get_request`; report `enriched: n of m` when capped. Failures of single enrichments are skipped silently.
- Explain per target: `automataJobEligible`, `autoApproval`, and `sourceEqualitySetId` (for `AI_JUDGEMENT` contexts all members of a set are listed — pick one representative per set; under `LATEST` only the newest member is listed here, the older ones are in `list_context_requests`).
- Guidance: "These are the requests you can create jobs on. You can also read the other members of their equality sets (`list_context_requests`), but not create jobs on them."

**`list_tenants(context_uuid)`** — row 91. Returns tenants with `authData` and `requestIds`. Guidance: "Authentication data is sensitive: use it only to understand which identity a request belongs to or to reproduce a finding; never include it in a finding's evidence verbatim — mask tokens." A tenant with an empty `requestIds` is an identity you may test with (e.g. access-control checks) but no in-context request belongs to it.

### 8.3 Request / response tools (`tools/requests.py`, `tools/responses.py`)

All require the request to be in the context (404 otherwise: say "not found or not in this context").

**`list_context_requests(context_uuid, equality_set_id=None, page=0, size=20)`** — row 118. Lists every request the agent can read, newest first, with `resolvedTarget` and `automataJobEligible`. Guidance: "`list_targets` shows only the requests you can create jobs on. Use this to find the older members of an equality set (pass `equality_set_id`) and read them with `get_request`; a request with `resolvedTarget=false` is readable but cannot be a job target."

**`get_request(context_uuid, request_id, include_body=true, body_max_bytes=None)`** — row 92 (+94).
- Calls metadata; if `include_body` and `body.storage != NONE` and the content is textual, also reads the body (section 9.2). Returns `{metadata..., body: {storage, contentType, mimeFamily, sizeBytes, text?, truncated?, binary?}}`.
- Guidance: first tool to understand a request: line, headers, body.

**`get_request_raw(context_uuid, request_id)`** — row 93.
- Calls `/raw`. Returns `{headText, bodyText?, rawRequestText?, rawRequestBase64?, responseId?, bodyStoredAsFile}`: `headText` is the decoded head; `bodyText` the decoded inline body when textual; `rawRequestText` = head + body as text with CRLF kept; `rawRequestBase64` = base64(head bytes + body bytes) — when the body is a file, fetch it through the stream route if it is ≤ the fetch cap, else omit and say why.
- Guidance: "Use this when you need the exact bytes of the request, e.g. to build the `rawRequestBase64` of a `fuzz` job's `custom_config` (put the FUZZ keyword where the wordlist entries go)."

**`get_request_body(context_uuid, request_id, offset=0, max_bytes=None)`** — row 94. Streams the body (section 9.2) and returns the slice. Use for large bodies or multipart.

**`get_response(context_uuid, request_id, include_body=true, body_max_bytes=None)`** — row 95 (+97). Same shape as `get_request` for the response (status code, headers, body). 404 → "this request has no stored response".

**`get_response_raw(context_uuid, request_id)`** — row 96. `{headText, bodyText?, bodyStoredAsFile}`.

**`get_response_body(context_uuid, request_id, offset=0, max_bytes=None)`** — row 97. Reading a JS/HTML/XML response body (stored as a file) goes through this tool; JSON bodies are also readable here.

### 8.4 Job tools (`tools/jobs.py`)

**`create_job(context_uuid, request_id, routine_id, ai_description, wordlist_ids, duration=None, rate=None, verbosity=None, priority=None, custom_config=None)`** — row 98.
- The server checks nothing itself except the argument types and `rawRequestText` (when `custom_config` has a `rawRequestText` string and no `rawRequestBase64`, it is replaced by `rawRequestBase64 = base64(normalize_raw_message(rawRequestText))`, section 9.4: format translation only). It does **not** fetch the context or the routine, and it does not validate the config. The core enforces every rule and its message is shown:
  - the request is a resolved target of the context (404), job-eligible and of the context's host (422);
  - `routine_id` is allowed in the context (400), the routine is available at the worker and allows `SINGLE_REQUEST` targets (400);
  - `custom_config` is valid against the routine's `configSchema` (400, with the violations);
  - `duration` is given or the context has a default (400); `ai_description` is present and at most 1 000 characters (400);
  - a job always has a rate of at least 1: `rate` when given must be at least 1, otherwise the rate is the host limit; either way it is capped by the host limit and the context's `maxRate` (a context always has a `maxRate`, a host always has a rate limit); `priority`, when given (or taken from the context), is between 1 and 5 (400); a host with a rate limit of 0 is closed to jobs (422 "Host N has a rate limit of 0, so no job can run on it" → tell the user, the agent cannot fix it);
  - every id of `wordlist_ids` exists (404 "Wordlist not found: [ids]"); the list is required, an empty one is allowed.
  There is **no** rule that a routine needs a wordlist: `Routine.wordlistRequired` is information (shown by `get_routine`/`list_routines`); the agent should read it, and ffuf-based routines (`fuzz`) fail at the worker without a wordlist.
- Then `POST`; read the job id from `Location`; fetch the job; return `{jobId, state, approved, configFileStatus, summary, next_step}` where `next_step` is one of: `"approved: call queue_job once configFileStatus is READY"`, `"needs human approval: a person must approve job N; you cannot queue it until then"`.
- Core errors to translate: 404 "Request not found in the agent context" → "that request is not one of your targets; call list_targets"; 404 "Wordlist not found: [..]" → "those wordlist ids do not exist; call list_wordlists"; 422 → "target is not job-eligible (its body is not empty/JSON/FORM) or belongs to another host"; every 400 → show the core's message.
- Description guidance: how to choose duration/rate (a given `rate` is at least 1 and is capped by the host limit and the context `maxRate`; a host with a limit of 0 refuses every job), that `wordlist_ids` must exist and that a routine with `wordlistRequired` is meant to run with one (read it in `get_routine`; the core does not enforce it), that `custom_config` shapes are per routine (read the routine's `configSchema` via `get_routine`), that the job is created as a DRAFT, that the job always runs as the request's own tenant.

**`list_jobs(context_uuid, states=None, page=0, size=20)`** — row 99. `states: list[JobState]|None`. Returns compact summaries and paging info.

**`get_job(context_uuid, job_id)`** — row 100. Returns the full job as the core gives it, including `terminal`, `waitingForAction` and, after a failure, `failureMessage` and `failureCategory` (all set by the core; the server derives nothing). The description explains the lifecycle (section 9.3) and the failure categories (section 17.C) so the agent can read `currentState`, `approved`, `configFileStatus` and the category.

**`list_request_jobs(context_uuid, request_id, states=None, page=0, size=20)`** — row 115. `request_id` must be an in-context request (404 otherwise). Returns every job that ever targeted it, also the ones of other contexts and of users, as compact summaries with `origin` and paging info. Guidance: "Call this before `create_job` on a request to see what was already tried, with which routine, and how it ended; do not repeat a test that already finished unless you change the parameters. `origin` tells whose job it is; `OTHER_CONTEXT` and `USER` jobs are read only for you (you cannot cancel or queue them)." Wide jobs are not included.

**`get_request_job(context_uuid, request_id, job_id)`** — row 116. Returns the job of the history with its `customConfig` (the flags or raw request it used), `aiDescription`, `aiResult`, failure message and `origin`; `terminal` and `waitingForAction` come from the core. 404 "that job did not target this request, or the request is not in your context". Guidance: use it to learn exactly what a past test did before designing the next one. For a job of this context use `get_job` for the full state.

**`get_request_job_result(context_uuid, request_id, job_id, offset=0, max_bytes=None)`** — row 117. Same result handling as `get_job_result` (section 9.3: always calls the core and shows its 409/404 with a hint, byte cap, `offset`, content type), against the history route. Results of other contexts' jobs may contain material from other identities: the same masking rule as for tenants applies when quoting them in a finding.

**`wait_for_jobs(context_uuid, job_ids, timeout_seconds=None, return_when="any")`** — aggregate over get_job (section 9.3).

**`get_job_result(context_uuid, job_id, offset=0, max_bytes=None)`** — row 101. See 9.3. Always calls the core and returns `{contentType, totalBytes?, offset, returnedBytes, truncated, text}`. The core's answers become tool errors with a hint, without any check of the job state in the server: 409 (the job has not ended) → "the job has not ended: use wait_for_jobs, then read the result again"; 404 → "no result: the job is not one of this context's AI jobs, or it ended without one (FAILED, CANCELED, ...); use get_job to see its state and failure message".

**`queue_job(context_uuid, job_id)`** — row 102. Core errors: `409` not approved → "waiting for a human to approve job N"; `409` files not READY → "files are still PREPARING (wait) or FAILED (call retry_job_preparation)"; `409` other state → show the message. On success return the new state (`TOQUEUE`) and the hint "the scheduler picks TOQUEUE jobs about every 60 s; use wait_for_jobs".

**`retry_job_preparation(context_uuid, job_id)`** — row 103. Only for a DRAFT job whose files are PREPARING (stuck) or FAILED. Returns "accepted: the files are being built again; check get_job".

**`set_job_ai_result(context_uuid, job_id, ai_result)`** — row 104. Requires a terminal state (the core answers 409 otherwise). Nothing is checked in the server: the core answers 400 above 5 000 characters, and the agent is told in the description to keep it short. Guidance: write the outcome summary: what was tested, what was observed, whether the hypothesis held, and the decision (finding / no finding / retry with other parameters).

**`cancel_job(context_uuid, job_id)`** — row 105. Core rule: cancelable in `TOQUEUE, QUEUED, RUNNING, PAUSED`; a `DRAFT` or ended job answers 409. A `TOQUEUE` job is canceled immediately. For `QUEUED`, `RUNNING` and `PAUSED` jobs the cancel is a *request* (`requestedState=CANCELED`) carried out when the worker next polls: say so and tell the agent to check `get_job`. A `PAUSED` job may have been paused by a human; canceling it is final.

### 8.5 Reference tools (`tools/reference.py`, `tools/wordlists.py`)

**`list_routines(query="", vulnerability_id=None, page=0, size=30)`** — row 50. **`get_routine(routine_id)`** — row 49 (returns `configSchema`, `description`, `resultContentType`, `allowedTargets`, `overhead`, `isAvailableAtConsumer` and `wordlistRequired`). Guidance: only routines in the context's `allowedRoutineIds` can be used in `create_job`; the briefing marks them.

**`list_vulnerabilities(query="", parent_id=None, page=0, size=30)`** — row 55. **`get_vulnerability(vulnerability_id)`** — row 54. Used to pick `vulnerability_id` for a finding.

**`list_wordlists(query="", vulnerability_id=None, page=0, size=30)`** — row 60. **`get_wordlist(wordlist_id)`** — row 59 (returns what the core gives: name, description, line count, size and sha256; the agent cannot read the file itself, row 123 is `f`).

**`create_wordlist(name, content, description=None, vulnerability_ids=None)`** — row 61. The server sends `content` (text, one entry per line) as the `file` part of a multipart request (file name = `name`) and the other arguments as the `metadata` JSON part; it checks nothing and adds no size cap of its own (the core answers 413 over 512 MB; over streamable HTTP the SDK limits one call to `AUTOMATA_HTTP_MAX_REQUEST_BYTES`, 4 MB by default, raise it there if needed; stdio has no such limit). Returns the new id (from `Location`). Translate: 409 → "a wordlist with exactly this content already exists (id in the message); use it"; 400 → show the core's message. Guidance: "Creates a wordlist from the text you give. The content can not be changed afterwards (only name, description and vulnerabilities can); to change it, create a new wordlist. Give a description so it can be chosen later."

### 8.6 Finding tools (`tools/findings.py`)

The core scopes the findings and enforces every rule (visible = the request of the finding is readable in the context, i.e. a request item or any member of an equality set item; editing only a finding created by this context's agent and not yet reviewed). The tools only call the scoped routes and show the core's messages.

**`create_finding(context_uuid, request_id, vulnerability_id, severity, confidence, status, evidence, poc_reproduction_steps=None, job_id=None, ai_description=None)`** — row 119. Returns the new finding id (from `Location`) and the hint "a human will review it; a human verdict (true/false positive) will appear in get_finding". 404 → "that request is not readable in this context, or the job is not one of your jobs that targeted it". Guidance: use `SUGGESTED` unless you have a reproducible proof (`CONFIRMED` needs PoC steps); evidence = what you observed (status codes, differing responses, snippets), mask secrets; `job_id` must be one of your own jobs on that request.

**`get_finding(context_uuid, finding_id)`** — row 121. Returns the finding including `humanVerdict`/`humanComment` (the learning signal) and its `origin`. 404 → "not found, or not about a request of this context".

**`list_findings(context_uuid, request_id=None, job_id=None, status=None, human_verdict=None, created_by=None, page=0, size=20)`** — row 120. Lists the findings of all requests readable in the context (including other contexts' and humans', each with its `origin`). Guidance: "Read this before creating a finding; the human verdicts show what is considered real. A finding can be on an older member of an equality set that `get_request` can still read, see `list_context_requests`."

**`update_finding(context_uuid, finding_id, severity=None, confidence=None, status=None, evidence=None, poc_reproduction_steps=None, ai_description=None)`** — row 122. Send only the provided fields. 409 → "only a finding you created in this context, and not yet reviewed by a human, can be edited". Returns the finding fetched again.

### 8.7 Ingestion and export (`tools/ingestion.py`, `tools/export.py`)

**`add_request(host, raw_request=None, raw_request_base64=None, raw_response=None, raw_response_base64=None, tenant_id=None, context_uuid=None)`** — row 114.
- `host` is the host name exactly as stored (e.g. `api.example.com`); it may be any host. Exactly one of `raw_request` / `raw_request_base64` is required; at most one of the response pair.
- Text inputs go through `normalize_raw_message` (section 9.4).
- `tenant_id` needs `context_uuid`; the core checks it and every other rule (section 7) and its 400/404 messages are shown.
- Returns the summary `{requestId, responseId?, hostId, method, computatedPath, source: "AI", ...}` and the note: "this request is not part of any context; you cannot read it back or create jobs on it until a human adds it to a context".
- Core errors: 404 "Host not found" → "no host with that name; hosts are managed by humans"; 400 → show the parser's message.

**`export_body_property_names(context_uuid, max_names=2000)`** — row 112. Resolve the context's host id (`get_host`) and call the export with `hostId` only (never `programId`, never another host). Returns `{names: [...], truncated, total}` (the response is one name per line). Guidance: "All JSON/form field names seen in the host's requests and responses; useful as a parameter wordlist."

---

## 9. Cross-cutting behaviour

### 9.1 Context handling
- `context_uuid` is validated with `uuid.UUID(...)` and passed in the path. There is no session state, so a restart or reconnect loses nothing, and no default context: the agent passes the uuid on every call.
- No tool lists contexts (the table forbids it): the agent gets the uuid from the human (prompt).

### 9.2 Bodies and text
`services/bodies.py`:
1. Stream with `client.stream("GET", ...)`; read at most `AUTOMATA_BODY_MAX_FETCH_BYTES`; skip `offset` bytes; keep up to `max_bytes` (default `AUTOMATA_BODY_MAX_BYTES`, clamp to the fetch cap). Always close the stream.
2. Decide "textual" from the response `Content-Type` family: `JSON, FORM, XML, HTML, JS, CSS, TEXT` and `application/*+json|xml`, `text/*`. Multipart is treated as text only if the bytes contain no NUL in the first 1 KiB; otherwise binary.
3. Textual → decode with the charset from `Content-Type` if present, else UTF-8, `errors="replace"`. Return `{text, encoding, offset, returnedBytes, totalBytes (from Content-Length if present else null), truncated}`.
4. Binary (`IMAGE, AUDIO, VIDEO, PDF, ARCHIVE, OCTET_STREAM`, NUL heuristic) → `{binary: true, contentType, sizeBytes}` and **no content** (optional later: return `IMAGE` through the SDK's image helper — **verify**, not in the MVP).
5. `body.storage == NONE` → no fetch, `{hasBody: false}`.
6. The head of a message (`/raw`) is decoded as ISO-8859-1.

### 9.3 Jobs, waiting and results
- **Lifecycle the tools must explain:** `create_job` → `DRAFT` (`approved` true/false) → (human approves if needed) → `queue_job` requires `approved` and `configFileStatus=READY` → `TOQUEUE` → the core's scheduler (every 60 s) moves it to `QUEUED` when rate limits allow → worker `RUNNING` → `FINISHED|FAILED|SELFTERMINATED|CANCELED` (`LOSTCONTACT` if the worker is silent for 30 minutes) → `get_job_result` → `set_job_ai_result`.
- **`wait_for_jobs`** (`services/waiting.py`): validates ids (at most `AUTOMATA_WAIT_MAX_JOB_IDS`) and `timeout_seconds` (default `AUTOMATA_WAIT_DEFAULT_SECONDS`, clamp to `AUTOMATA_WAIT_MAX_SECONDS`); loop: `get_job` for every job that is not `terminal` (concurrently) every `AUTOMATA_WAIT_POLL_SECONDS`; after each poll call `ctx.report_progress(...)` with a short message (keeps some clients' timers alive — **verify**). The server knows no state meaning: it only reads the `terminal` and `waitingForAction` fields of the core. Returns early when:
  - `return_when="any"` (default): any job is `terminal`; `"all"`: all are `terminal`;
  - every job that is not `terminal` is `waitingForAction` (nothing will happen until someone acts);
  - the deadline passes (`timedOut: true`).
  Result: `{jobs: [{jobId, currentState, requestedState, approved, configFileStatus, terminal, waitingForAction, failureMessage?}], timedOut, terminalJobIds, waitingForActionJobIds, next_steps}`. The agent calls it again until it is satisfied. Poll errors: a transient 5xx/timeout on one poll is skipped; `AUTOMATA_WAIT_MAX_POLL_ERRORS` in a row fail the call.
- **`get_job_result`** (and `get_request_job_result`, same logic against the history route): the server does not decide which states have a result; it always streams `.../result` from the core. The core answers 409 "The job has to end before downloading its result" while the job is not `FINISHED` or `FAILED`, and 404 "Result not found" when the file does not exist (the worker uploads one only for a `FINISHED` job, so a `FAILED`, `CANCELED`, `SELFTERMINATED` or `LOSTCONTACT` job has none). Both become tool errors with the hints given in the tool entry; the server does not read the message to tell the 404 cases apart. A result is read with the same byte-cap logic (`AUTOMATA_RESULT_MAX_BYTES`, `offset`), content type `application/json` or `text/plain`. For JSON results, if truncated, say so and suggest paging with `offset`. (Later, optional: a summarizer for ffuf's JSON — section 14, phase 11.)
- Core limits (it answers 400 above them): `ai_description` at most 1 000 characters, `ai_result` at most 5 000. The server does not check them and never truncates: the core's 400 names the limit, and the tool descriptions state both limits so the agent keeps its text short. The job result file itself (`get_job_result`) can be large and is read with `offset`/`max_bytes`, unrelated to these limits.

### 9.4 Raw HTTP messages (`services/raw_messages.py`)
- `normalize_raw_message(text) -> bytes`: split at the first blank line (accept `\n\n`, `\r\n\r\n`); convert line endings of the **head** to CRLF; make sure the head ends with a blank line (`\r\n\r\n`); keep the body unchanged; encode UTF-8. Do not recompute `Content-Length` (the core does not validate it) but mention it in the tool description.
- `to_base64(bytes)`, `compose_message(head_b64, body_b64|None|bytes) -> bytes`.
- `rawRequestText` convenience for `custom_config` of routines whose schema has `rawRequestBase64` (key `fuzz`): if `custom_config` contains `rawRequestText` (a string) and no `rawRequestBase64`, replace it by `rawRequestBase64 = base64(normalize_raw_message(rawRequestText))` before validation. It is an MCP-side convenience, documented in `create_job`'s description; the core only ever sees `rawRequestBase64`.

### 9.5 Error mapping (`core/errors.py`)
Core errors become exceptions with agent-facing messages. The core's text is included verbatim after the guidance.

| Core | Meaning | Message guidance |
|---|---|---|
| 404 | missing, or not in the context | "Not found, or not part of this context: <core message>. Use list_targets / list_jobs to see what you can access." |
| 400 | invalid argument | "The core rejected the arguments: <message>. Fix the arguments and retry." |
| 409 | job state / not approved / finding not editable / conflict | tool-specific hint (see each tool; for `update_finding`: only a finding you created in this context and no human has reviewed can be edited); generic (implemented, the message is not parsed): "The core refused because of the current state (job state, approval, finding editable, or a change made at the same time): <message>. Re-read the item before deciding what to do." |
| 422 | ineligible target | "That cannot be a job target: <message>." (a request that is not job-eligible, another host, or a host with a rate limit of 0 that the agent cannot change: tell the user) |
| 413 | wordlist file too large | "The wordlist is over the core's limit (512 MB): <message>. Send a smaller file." |
| 501 | not implemented | "Not supported by the core: <message>." |
| 5xx | core failure | "The core failed (HTTP N). It may be temporary; retry once. Do not retry in a loop." |
| 415 and any other 4xx | refused request | "The core refused the request (HTTP N): <message>." (`CoreRejected`) |
| connect error / timeout | core unreachable | "Cannot reach the core at <url> (timeout). Tell the user." (GETs are retried per config first) |

Never expose stack traces or the core URL credentials (none exist). Truncate core messages to 1 000 characters.

*As implemented (`core/errors.py`):* `CoreError(message, status, core_message)` is the base; `CoreNotFound` (404), `CoreBadRequest` (400), `CoreConflict` (409), `CoreUnprocessable` (422), `CorePayloadTooLarge` (413), `CoreNotImplemented` (501), `CoreServerError` (other 5xx), `CoreRejected` (other 4xx), `CoreUnreachable` (no answer; the text names the core URL, and for writes says the request may or may not have been applied) and `CoreResponseError` (an answer that cannot be read, section 6.4). `str(error)` is the guidance followed by the core's message; tools that need a specific hint (sections 8.4–8.7) catch the class and use `status` and `core_message`. An empty core body becomes "(no message)".

---

### 9.6 Server assembly as implemented (phase 2, `server.py`)

- `create_server(settings) -> MCPServer[AppState]` builds the server named `automata` with short `instructions` (what the tools are for, that the user gives the `context_uuid`, that the core's messages explain refusals; it names no tool, so it stays true while tools are added), the logging middleware and `register_all`. The **lifespan** opens one `CoreClient` (`from_settings`), runs `check_core`, yields `AppState(core, settings)` and closes the client on shutdown. It runs once when the server starts (stdio) or when the HTTP app starts, not per request.
- `check_core(core)` calls `GET /api/routines?size=1` (a read-only route the agent may use anyway) and logs: INFO when the core answers, WARNING when it is unreachable ("The server starts anyway; tools fail until the core is up") or answers an error. It never raises, so a core that starts later does not need a server restart.
- `run_server(mcp, settings)`: `stdio` runs `mcp.run("stdio")`; `streamable-http` runs `mcp.run("streamable-http", host, port, max_request_body_size)` from `http_run_options(settings)`. A server instance can serve over HTTP once (the SDK's session manager runs only once per instance); the process creates one, so this only matters in tests.
- Entry points: the `automata-mcp` console script (`[project.scripts]` → `automata_mcp.__main__:main`) and `python -m automata_mcp`.

## 10. Prompts and resources

### 10.1 Prompt `test_context(context_uuid)`
Returns the workflow the agent should follow (adapt the wording, keep the steps — it is the user's "AI Test Flow"):

1. Call `get_context_briefing` and read it fully: your targets, tenants, host, routines, rate limits, prior findings.
2. Understand each target (`get_request`, `get_response`, `get_response_body` for scripts/HTML). Targets of one equality set are equivalent — pick one representative per set. Under `LATEST` only the newest request of a set is a target; the older ones are readable (`list_context_requests`) but a job cannot target them.
3. Decide what to test and why. For each test create a job (`create_job`) with an `ai_description` stating the goal: *what the result will let us verify*. Only job-eligible targets can be jobs; test the others with your own reasoning and, if you observe new traffic, record it with `add_request`.
4. A job may need human approval (`approved=false`): tell the user and wait; you cannot queue it. Otherwise `queue_job` once its files are READY.
5. Use `wait_for_jobs` (not repeated `get_job`) until jobs end. Handle failures with the `failureCategory` and `failureMessage` of the job (section 17.C): fix the config for `INVALID_CONFIG`, tell the user for the worker-side categories, and read the message for `TOOL_EXIT`.
6. For each finished job read `get_job_result`, then write your conclusion with `set_job_ai_result`.
7. If a vulnerability is verified, `create_finding` (with evidence, severity, confidence, PoC steps when confirmed). Read `list_findings` first: humans' verdicts on earlier findings show what they consider real.
8. Rules: stay inside your context; never put credentials in findings; read the job history of a request (`list_request_jobs`) before creating a job on it and do not repeat a test that already ended unless you change something; cancel only jobs you started and no longer need.

### 10.2 Resources (read-only, optional but cheap)
- `automata://context/{context_uuid}/briefing` → the briefing as JSON.
- `automata://routine/{routine_id}` → routine detail incl. `configSchema`.
Tools cover everything; resources only help clients that browse them.

---

## 11. Soft guardrails

### 11.1 (removed)
The loop detector for identical jobs was dropped: the approval gate, the rate caps and the job history already bound the load, and a hard limit would belong to a per-context job budget in the core (gap C9). The workflow prompt (section 10) still tells the agent to read the job history before creating a job.

### 11.2 (removed)
The finding scope checks live in the core (scoped finding routes, rows 119–122). Nothing is checked in this server.

### 11.3 (removed)
The wordlist rules (size, duplicate file, empty or non-text file) live in the core. Nothing is checked in this server.

### 11.4 Other
- Size caps on everything returned (sections 5, 9.2, 9.3).
- Never log tool results or arguments of `add_request` / `list_tenants`.
- `export_body_property_names` is pinned to the context's own host.

---

## 12. Known gaps and recommended core changes

None of these blocks the MVP; each would be a separate core branch (the core repo's rules apply). Decide with the user before doing any.

| # | Gap | Effect now | Recommended core change |
|---|---|---|---|
| C1 | ~~Finding routes are not context-scoped~~ **Done** (core, rows 119–122) | the tools use the scoped routes | — |
| C2 | ~~`POST /api/wordlists` accepts any path~~ **Done** (core: the file is uploaded and stored in RustFS, there is no path) | the tool uploads the text through the route | — |
| C3 | Export is by `hostId` without a context | pinned by MCP | optional: `GET /api/agent-contexts/uuid/{uuid}/export/body-properties` |
| C4 | Requests added with `add_request` are not in any context | the agent cannot read them back or create jobs on them (by decision) | none required; a human adds them to a context |
| C5 | A `DRAFT` job cannot be canceled | a draft the agent no longer wants stays | none; the agent just does not queue it |
| C6 | No finish notification | `wait_for_jobs` polls | SSE `GET /api/agent-contexts/uuid/{uuid}/events` (core enhancement plan §4) |
| C7 | Job results are raw tool output (`ffuf` JSON, `masser` output) | the agent reads raw text | structured `{jobId}.summary.json` written by the worker (enhancement plan §3); preferred over the server-side summariser of phase 11 |
| C8 | ~~Failure classification is MCP-side~~ **Done** (core and worker: `failureCategory` on the job) | the tools return the category | — |
| C9 | No budgets / test plans / audit log | no hard limit on jobs per context | enhancement plan §1, §2, §9 |
| C11 | Nothing stops the same job from being created many times (the loop detector was dropped) | the approval gate, the rate caps and the job history bound the load | a per-context job budget (with C9) if a hard limit is wanted |
| C10 | `TargetCardinalityPair.MULTILPLE_HOSTS` typo; `AutomataApplicationTests` needs a datasource | cosmetic / unrelated | optional |

---

## 13. Testing strategy

- **Unit tests** for every function (project rule): `services/*` (pure logic), `core/errors.py`, `core/paging.py`, models parsing, each tool with the core mocked by `respx`.
- **Fixtures:** `tests/fixtures/core/*.json` — one real-shaped sample per payload in section 7 (copy the shapes from the core DTOs; refresh them when the core changes). Tests parse every fixture with the pydantic models and assert round-trips of the request bodies.
- **As done in phase 1:** `tests/conftest.py` has a `load_fixture(name)` fixture; the 28 fixtures in `tests/fixtures/core/` cover every payload of section 7, including the bodies the server sends (these are asserted to round-trip through `to_payload()`); client tests use `respx` with an injected fake `sleep` (so retry back-offs are asserted without waiting). Parametrised tables cover every GET route (path, query, result type).
- **As done in phase 2:** an autouse fixture `clean_automata_env` (in `tests/conftest.py`) removes every `AUTOMATA_*` variable from each test; `tests/unit/test_tool_registry.py` keeps the 37 names of 17.A and a set `IMPLEMENTED` that each phase extends, asserting the server registers exactly that set; the HTTP app is tested with Starlette's `TestClient` (a new server per app, because a session manager can run only once); `python -m automata_mcp` and the console script entry are tested. The real commands were also run once by hand (stdio client against `uv run automata-mcp`; `curl` initialize against the HTTP server) with no core running: both start, serve and log to stderr.
- **Tool-level tests through the MCP protocol** with an in-memory client/server pair (in `mcp` 2.x the old helper `mcp.shared.memory.create_connected_server_and_client_session` is gone; **verified in phase 2:** `async with mcp.Client(server) as client:` connects to an `MCPServer` in memory, runs its lifespan and offers `list_tools()` and `call_tool(name, args)`; a result has `is_error` and `content[0].text`): list tools (assert the exact tool name set = section 17.A), call a tool, assert the structured result and that errors come back as `isError`.
- **Must-have cases:** each core status (404/400/409/422/5xx/timeout) for at least one tool of each group; `wait_for_jobs` early exits (any, all, waitingForAction, timeout) with a fake clock/sleep; byte-cap truncation and offset; binary detection; `normalize_raw_message` (LF→CRLF, missing blank line, body untouched); `rawRequestText` conversion (and that `create_job` makes no call other than the POST and the job fetch); finding tools only call the scoped routes (and show 404/409 messages); `export_body_property_names` never sends `programId`.
- **Integration tests** (`tests/integration`, opt-in via `AUTOMATA_IT=1`, real core on `AUTOMATA_CORE_URL` with a prepared context): one happy path per tool group. Not required for CI.
- **Contract check** (`scripts/check_core_contract.py`): fetch the core's OpenAPI (`{core}/v3/api-docs`, springdoc is enabled — **verify** the path) and assert every method+path in `core/client.py` exists. Run manually and in phase 9.
- Quality gates per branch: `ruff check`, `ruff format --check`, `mypy --strict src`, `pytest`.

---

## 14. Implementation phases

Every phase = branches from `staging`, tests included, docs (`docs/core-contract.md`, README) updated in the same change. Branch names carry a verb. Order matters only where a "needs" is stated.

| # | Branch(es) | Work | Needs |
|---|---|---|---|
| 0 | `chore/set-up-project-skeleton` | `git init`, `main` + `staging`; `pyproject.toml` (uv), `.gitignore`, `.env.example`, ruff/mypy/pytest config, empty package, CI-less Makefile or `justfile` for the gates | — |
| 1 | `feat/add-core-client` | `config.py`, `core/` (models for all payloads and `models/enums.py` for the enums of 6.3, `CoreClient` with one method per route in 6.2, retries for GETs, error mapping, paging), `docs/core-contract.md`, fixtures | 0 |
| 2 | `feat/add-server-skeleton` | `server.py` (MCPServer, lifespan creating `AppState`), `__main__`, the `automata-mcp` console script (`[project.scripts]` in `pyproject.toml`, needed by `uv run automata-mcp`, section 15), transports (stdio + streamable HTTP; in `mcp` 2.x host and port are arguments of `run_streamable_http_async`, default host `127.0.0.1`, so pass the settings explicitly), logging, startup log of core reachability, tool-name registry test through the in-memory `mcp.Client` (section 13), the server `instructions`, the middleware that logs tool calls (8.1) | 1 |
| 3 | `feat/add-context-tools`, `feat/add-reference-tools` | section 8.2 (without the briefing's jobs/findings parts), 8.5 (without `create_wordlist`) | 2 |
| 4 | `feat/add-request-response-tools` | `services/bodies.py`, `raw_messages.py`, section 8.3 (including `list_context_requests`) | 2 |
| 5 | `feat/add-job-tools` | `config_validation.py` (only the `rawRequestText` conversion), `waiting.py`, section 8.4 (all twelve tools), briefing's jobs part | 3, 4 |
| 6 | `feat/add-finding-tools`, `feat/add-ingestion-and-export-tools`, `feat/add-wordlist-creation-tool` | section 8.6, 8.7, `create_wordlist`; briefing's findings part; `tests/integration` (opt-in with `AUTOMATA_IT=1`, one happy path per tool group, section 13), as its own branch `feat/add-integration-tests` once every group exists | 5 |
| 7 | (dropped) | the loop detector was dropped (section 11.1); the number is kept because the phases are referenced | — |
| 8 | `feat/add-workflow-prompt-and-resources` | section 10 | 6 |
| 9 | `feat/add-contract-check-script`, `feat/add-docker-packaging` | `scripts/check_core_contract.py`; `Dockerfile` (python slim, uv sync, non-root, `AUTOMATA_TRANSPORT=streamable-http`), `.dockerignore`, README run instructions (the Host-header check of the SDK was already verified in phase 2, section 5: nothing to configure when bound to `0.0.0.0`) | 6 |
| 10 | (core repo) `feat/add-mcp-service-to-compose` | add an `automata-mcp` service to the core's `docker-compose.yml` (build `../../../automata-mcp`, env `AUTOMATA_CORE_URL=http://app:8080`, port 8000, `depends_on: app`) | 9 |
| 11 | optional: `feat/add-ffuf-result-summary` | only if the worker-side summary of C7 is not done: summarise ffuf JSON results (counts by status/size/words, outliers) in `get_job_result(summary=true)` (a parameter that section 8.4 does not have until this phase; it is presentation of raw output, not a rule); do **not** guess masser's output format. If the worker writes `{jobId}.summary.json`, return that file instead and parse nothing | 5 |

**Status:** phase 0 (`chore/set-up-project-skeleton`), phase 1 (`feat/add-core-client`) and phase 2 (`feat/add-server-skeleton`) are done and merged into `staging`. Nothing is merged into `main` (the user's decision for now). Phase 3 and later have not started.

**Definition of done per phase:** gates pass; every new/changed function has tests; `docs/` updated; the tool-name set test matches section 17.A for the tools that exist so far; the user confirmed before any commit/merge.

---

## 15. Deployment

- **Local (stdio):** the MCP client launches `uv run automata-mcp` (or the console script) with `AUTOMATA_CORE_URL` in its environment. Example client config entry: command `uv`, args `["--directory", "/home/haidar/dev/automata-mcp", "run", "automata-mcp"]`, env `AUTOMATA_CORE_URL`.
- **Docker (streamable HTTP):** `AUTOMATA_TRANSPORT=streamable-http`, port 8000, one container can serve several agent sessions (the server is stateless). The compose service (phase 10) points `AUTOMATA_CORE_URL` at `http://app:8080`.
- No secrets exist in this server's configuration (no auth anywhere). Do not publish the HTTP port beyond the trusted network: the server has no authentication either, so it is as open as the core.

---

## 16. Open decisions (defaults to use unless the user says otherwise)

| # | Decision | Default |
|---|---|---|
| D1 | `context_uuid` as an explicit parameter of every tool vs. a session "active context" | explicit required parameter on every tool, no env default (decided by the user) |
| D2 | MCP-side `rawRequestText` convenience for `fuzz` custom config | yes (9.4) |
| D3 | `wait_for_jobs` defaults | poll 5 s, default wait 45 s, max 55 s, `return_when="any"` |
| D4 | `create_wordlist` guard | none in the MCP; the core validates the file (C2 done) |
| D5 | Finding scoping | enforced by the core's scoped finding routes (C1 done) |
| D6 | 37 tools vs. fewer | keep all 37; names are verbs and descriptions short |
| D7 | `list_targets` enrichment | on by default, capped at 50 |
| D8 | Default transport | stdio; streamable HTTP in Docker |
| D9 | Project tooling | `uv` + ruff + mypy + pytest |
| D10 | ffuf result summariser | later phase (11), not MVP |
| D11 | Image bodies returned as MCP images | not in MVP |

---

## 17. Appendices

### 17.A Coverage matrix: operations-table rows marked `t` → tools

Row numbers are those of the table in the core repo (`.claude/mcp-operations-access.md`, 123 rows; row 123, download of a wordlist file, is `f` and has no tool).

| Row | Operation | Tool |
|---|---|---|
| 49 | Get routine details | `get_routine` |
| 50 | List routines | `list_routines` |
| 54 | Get vulnerability | `get_vulnerability` |
| 55 | List vulnerabilities | `list_vulnerabilities` |
| 59 | Get wordlist | `get_wordlist` |
| 60 | List wordlists | `list_wordlists` |
| 61 | Create wordlist | `create_wordlist` |
| 81 | Get agent context by UUID | `get_context` |
| 89 | Resolved requests (by UUID) | `list_targets` |
| 90 | Host details of the context | `get_host` |
| 91 | Tenants of the context | `list_tenants` |
| 92 | Request metadata | `get_request` |
| 93 | Raw request | `get_request_raw` |
| 94 | Request body stream | `get_request_body` |
| 95 | Response metadata | `get_response` |
| 96 | Raw response | `get_response_raw` |
| 97 | Response body stream | `get_response_body` |
| 98 | Create AI job | `create_job` |
| 99 | List the context's jobs | `list_jobs` |
| 100 | Get a job | `get_job` |
| 101 | Job result | `get_job_result` |
| 102 | Queue a job | `queue_job` |
| 103 | Retry preparation | `retry_job_preparation` |
| 104 | Set AI result | `set_job_ai_result` |
| 105 | Cancel a job | `cancel_job` |
| 119 | Create finding (scoped) | `create_finding` |
| 120 | List the findings of the readable requests | `list_findings` |
| 121 | Get a finding | `get_finding` |
| 122 | Update a finding | `update_finding` |
| 112 | Export body property names | `export_body_property_names` |
| 114 | AI adds a request | `add_request` |
| 115 | List the jobs that targeted a request | `list_request_jobs` |
| 116 | Get one of those jobs | `get_request_job` |
| 117 | Result of one of those jobs | `get_request_job_result` |
| 118 | List the readable requests of the context | `list_context_requests` |
| — | (aggregate) | `get_context_briefing`, `wait_for_jobs` |

35 rows + 2 aggregates = **37 tools**. The full tool-name set (assert it in a test): `get_context_briefing, get_context, get_host, list_targets, list_tenants, get_request, get_request_raw, get_request_body, get_response, get_response_raw, get_response_body, create_job, list_jobs, get_job, wait_for_jobs, get_job_result, queue_job, retry_job_preparation, set_job_ai_result, cancel_job, list_routines, get_routine, list_vulnerabilities, get_vulnerability, list_wordlists, get_wordlist, create_wordlist, create_finding, get_finding, list_findings, update_finding, export_body_property_names, add_request, list_request_jobs, get_request_job, get_request_job_result, list_context_requests`.

### 17.B Sample payloads

`create_job` body sent to the core (`POST /api/agent-contexts/uuid/6f1c…/jobs`):
```json
{
  "requestId": 7,
  "aiDescription": "Fuzz the id path parameter of GET /api/users/{id} to check for IDOR via sequential and malformed ids",
  "routineId": 1,
  "duration": "SHORT",
  "wordlistsIds": [3],
  "rate": 20,
  "verbosity": "SUMMARIZED",
  "priority": 1,
  "customConfig": {
    "rawRequestBase64": "R0VUIC9hcGkvdXNlcnMvRlVaWiBIVFRQLzEuMQ0KSG9zdDogYXBpLmV4YW1wbGUuY29tDQoNCg==",
    "flags": [{"key": "-mc", "value": "200,403"}]
  }
}
```
The agent's `custom_config` may instead contain `"rawRequestText": "GET /api/users/FUZZ HTTP/1.1\nHost: api.example.com\n\n"`; the server converts it.

`add_request` body sent to the core (`POST /api/external/ai/requests`):
```json
{
  "requestBase64": "UE9TVCAvYXBpL2xvZ2luIEhUVFAvMS4xDQpIb3N0OiBhcGkuZXhhbXBsZS5jb20NCkNvbnRlbnQtVHlwZTogYXBwbGljYXRpb24vanNvbg0KDQp7InUiOiJhIn0=",
  "host": "api.example.com",
  "base64Response": "SFRUUC8xLjEgMjAwIE9LDQpDb250ZW50LVR5cGU6IGFwcGxpY2F0aW9uL2pzb24NCg0KeyJvayI6dHJ1ZX0=",
  "tenantId": 4,
  "agentContextUuid": "6f1c0a52-0000-4000-8000-000000000001"
}
```

`create_finding` body sent to the core (`POST /api/agent-contexts/uuid/6f1c…/findings`):
```json
{
  "requestId": 7, "jobId": 31, "vulnerabilityId": 12, "severity": 7.5, "status": "CONFIRMED",
  "confidence": 8.0,
  "evidence": "GET /api/users/2 with tenant A's token returned tenant B's profile (200, 412 bytes)",
  "pocReproductionSteps": "1. Log in as tenant A. 2. GET /api/users/2. 3. Observe tenant B's email in the body.",
  "aiDescription": "Broken object-level authorization on /api/users/{id}"
}
```

`get_job` (core `HttpJobResponseDto`, abbreviated):
```json
{"jobId": 31, "httpJobScope": "NARROW", "currentState": "DRAFT", "requestedState": null, "verbosity": "SUMMARIZED",
 "priority": 1, "rate": 20, "routine": {"id": 1, "key": "fuzz"}, "configFileStatus": "READY",
 "aiDescription": "Fuzz the id path parameter ...", "aiResult": null, "createdBy": "AI", "approved": false,
 "agentContextUuid": "6f1c0a52-0000-4000-8000-000000000001", "failureMessage": null, "failureCategory": null,
 "terminal": false, "waitingForAction": true}
```

`get_request` metadata (core `RequestMetadataResponse`):
```json
{"requestId": 7, "hostId": 2, "programId": 1, "tenantId": 4, "responseId": 8, "source": "BURP",
 "insertionDate": "2026-10-20",
 "line": {"method": "POST", "computatedPath": "/api/users/{id}", "extension": null, "version": "HTTP/1.1",
          "target": "/api/users/42?expand=items"},
 "headers": [{"name": "Host", "value": "api.example.com"}, {"name": "Content-Type", "value": "application/json"}],
 "body": {"storage": "INLINE", "contentType": "application/json", "mimeFamily": "JSON", "sizeBytes": 183}}
```

### 17.C Failure categories (set by the worker, returned by the core)

The server classifies nothing. When a job ends in `FAILED` or `SELFTERMINATED` the worker reports a `failureCategory` with the message; the core stores it (the first report only) and returns it as `failureCategory` of the full job and of the request-history job. `failureMessage` is cut to 500 characters. A worker that reports no category, or a name the core does not know, gives `UNKNOWN`. A job that did not fail has no category. The tool descriptions give the agent this table:

| Category | Meaning | What the agent should do |
|---|---|---|
| `INVALID_CONFIG` | the job details could not be parsed or turned into a command (unknown flag, no wordlist for ffuf, an unreadable `customConfig`) | fix `custom_config` or the wordlists (re-read the routine's `configSchema` and `wordlistRequired`) and create a new job; an unchanged retry fails again |
| `PREPARATION` | the worker could not set the job up: work directory, files download, routine mapping | not the agent's mistake: tell the user, or retry later with the same parameters |
| `POLL` | the first poll of the core failed | same: infrastructure, tell the user or retry later |
| `TOOL_START` | the connector or the tool could not be started, or the connector did not connect in time | same: infrastructure |
| `TOOL_EXIT` | the tool ran and exited with an error; the reason is not known to the core | read `failureMessage`; consider a lower `rate` or other flags; do not retry blindly |
| `RESULT` | the job finished without a result file, or the file could not be uploaded | infrastructure: tell the user |
| `UNKNOWN` | nothing more is known (an old worker, a heartbeat timeout, `LOSTCONTACT`) | read the message; retry at most once with changes |

State `LOSTCONTACT` has its own meaning: the worker stopped reporting and the result is lost, ask the user to check the worker. `configFileStatus=FAILED` is not a worker failure: call `retry_job_preparation` (draft jobs only). The tool text must not claim to know why a tool exited: the core only has the exit status.

### 17.D End-to-end example (what a good session looks like)

1. `get_context_briefing(ctx)` → 3 targets (2 job-eligible, 1 multipart), 2 tenants, routines `fuzz`/`masser`.
2. `get_request(ctx, 7)`, `get_response(ctx, 7)` → understands `GET /api/users/{id}`.
3. `get_routine(1)` → reads `configSchema`; `list_wordlists(query="ids")` → wordlist 3.
4. `create_job(ctx, 7, 1, "…IDOR…", [3], custom_config={"rawRequestText": "GET /api/users/FUZZ …", "flags": []})` → job 31, `approved=false`.
5. Tell the user: "job 31 needs approval". Human approves.
6. `queue_job(ctx, 31)` → `TOQUEUE`; `wait_for_jobs(ctx, [31])` → returns `RUNNING`… repeated until `FINISHED`.
7. `get_job_result(ctx, 31)` → analyses; `set_job_ai_result(ctx, 31, "…")`.
8. `list_findings(ctx)` to see earlier verdicts; `create_finding(ctx, 7, 12, …, job_id=31)`.

### 17.E Glossary
- **Context / agent context:** the unit of work given to an agent: a host, a set of requests/equality sets, tenants, allowed routines, defaults.
- **Target (resolved target):** a request a job can target, resolved from a context item (under `LATEST` the newest of a set; under `AI_JUDGEMENT` all of a set). **Readable request:** a request item or any member of an equality set item under both strategies (`list_context_requests`); findings are visible for the readable requests.
- **Equality set:** requests the core considers equivalent (same method/path/shape).
- **Routine:** a kind of job (`fuzz` → ffuf, `masser`), with a `configSchema` for its `customConfig`.
- **Job eligible:** a request whose body is empty, JSON or FORM; others (multipart, XML, …) cannot be job targets.
- **Auto-approval:** whether a job on a target needs no human approval (decided by the context/equality set).
- **terminal / waitingForAction:** fields the core computes on a job: `terminal` = it ended; `waitingForAction` = a DRAFT that needs someone to act (an AI job nobody approved, or failed config files).
- **Worker/orchestrator:** the Go processes that run jobs; invisible to this server.
