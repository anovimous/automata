# Contract with the Automata core

The routes `automata-mcp` calls, the payloads it sends and reads, and what is known about them. The
plan (`docs/implementation-plan.md`, sections 6 and 7) says what to build; this file says what the
code of the core actually does and where the client and the models implement it.

**Source:** the core repo (`/home/haidar/dev/automata/code/automata`), branch `staging` at commit
`d7e0297`, read on 2026-10-26 (controllers, DTO records, `GlobalExceptionHandler`, `JacksonConfig`).
Nothing here was run against a live core. Keep this file in line with the core: a change to a route,
a payload or a rule on one side must be checked against the other side.

Sample payloads of every DTO are in `tests/fixtures/core/*.json`; the tests parse each one with the
models in `src/automata_mcp/core/models/`.

## Conventions of the core

- **Errors** are plain text (the exception message), mapped by `GlobalExceptionHandler`:
  `404` entity not found; `400` illegal argument, unreadable body, missing multipart part;
  `409` invalid job state, job not approved, finding not editable, wordlist exists or in use,
  host in use, optimistic-lock conflict; `413` upload over the multipart limit (body "The uploaded
  file is too large"); `415` unsupported media type; `422` ineligible job target; `501` unsupported
  operation; `500` anything else. `core/errors.py` maps each to an exception.
- **JSON:** `JacksonConfig` disables `FAIL_ON_UNKNOWN_PROPERTIES` and `WRITE_DATES_AS_TIMESTAMPS`
  and registers the Java time and `Jdk8` (Optional) modules. There is no `NON_NULL` inclusion, so
  the core sends `null` for empty fields; the models treat `null` and absent alike. Dates are ISO
  strings (`LocalDate` `2026-10-20`, `Instant` `2026-10-20T09:15:30Z`) and are kept as strings.
- **Pages** (`PageHolderResponse`): `{content, page, size, totalElements, totalPages, last}`.
  `page` is 0-based. The context requests and findings routes cap `size` at 100 themselves
  (`MAX_PAGE_SIZE`); the client clamps `size` to 1..100 for every list. Default sizes: 20 for the
  context routes, 30 for routines, vulnerabilities and wordlists.
- **Creation** answers `201` with a `Location` header and an empty body, except `add_request`
  (`201` with the `RequestSummaryResponse`, no `Location`). The client returns the id from the
  last segment of `Location`.
- **Optional fields of a PATCH** (`FindingPatchRequest`) are `Optional<...>`; with the `Jdk8` module
  an absent or null field is `Optional.empty()`. The client still sends only the fields to change,
  and the two flags (`setPocReproductionStepsNull`, `setAiDescriptionNull`) only when true.

## Routes used

`{uuid}` is the context UUID. `base` = `/api/agent-contexts/uuid/{uuid}`. Query parameters are those
the client sends; `page` and `size` are always sent (clamped).

| Client method | Route | Query | Success |
|---|---|---|---|
| `get_context` | `GET base` | | 200 |
| `list_resolved_targets` | `GET base/resolved-requests` | | 200 list |
| `get_host` | `GET base/host` | | 200 |
| `list_tenants` | `GET base/tenants` | | 200 list |
| `list_context_requests` | `GET base/requests` | `equalitySetId`, paging | 200 page |
| `get_request_metadata` | `GET base/requests/{rid}/metadata` | | 200 |
| `get_request_raw` | `GET base/requests/{rid}/raw` | | 200 |
| `stream_request_body` | `GET base/requests/{rid}/body/stream` | | 200 bytes |
| `get_response_metadata` | `GET base/requests/{rid}/response/metadata` | | 200 |
| `get_response_raw` | `GET base/requests/{rid}/response/raw` | | 200 |
| `stream_response_body` | `GET base/requests/{rid}/response/body/stream` | | 200 bytes |
| `create_job` | `POST base/jobs` | | 201 + `Location` |
| `list_jobs` | `GET base/jobs` | `states` (comma list), paging | 200 page |
| `get_job` | `GET base/jobs/{jid}` | | 200 |
| `stream_job_result` | `GET base/jobs/{jid}/result` | | 200 bytes |
| `queue_job` | `POST base/jobs/{jid}/queue` | | 204 |
| `retry_job_preparation` | `POST base/jobs/{jid}/retry-preparation` | | 202 |
| `set_job_ai_result` | `POST base/jobs/{jid}/ai-result` | body `{aiResult}` | 204 |
| `cancel_job` | `POST base/jobs/{jid}/cancel` | | 204 |
| `list_request_jobs` | `GET base/requests/{rid}/jobs` | `states`, paging | 200 page |
| `get_request_job` | `GET base/requests/{rid}/jobs/{jid}` | | 200 |
| `stream_request_job_result` | `GET base/requests/{rid}/jobs/{jid}/result` | | 200 bytes |
| `list_routines` | `GET /api/routines` | `query`, `vulnId`, paging | 200 page |
| `get_routine` | `GET /api/routines/{id}` | | 200 |
| `list_vulnerabilities` | `GET /api/vulnerabilities` | `query`, `parentVulnId`, paging | 200 page |
| `get_vulnerability` | `GET /api/vulnerabilities/{id}` | | 200 |
| `list_wordlists` | `GET /api/wordlists` | `query`, `vulnId`, paging | 200 page |
| `get_wordlist` | `GET /api/wordlists/{id}` | | 200 |
| `create_wordlist` | `POST /api/wordlists` | multipart: `file`, optional JSON part `metadata` | 201 + `Location` |
| `create_finding` | `POST base/findings` | | 201 + `Location` |
| `get_finding` | `GET base/findings/{fid}` | | 200 |
| `list_findings` | `GET base/findings` | `requestId`, `jobId`, `status`, `humanVerdict`, `createdBy`, paging | 200 page |
| `update_finding` | `PATCH base/findings/{fid}` | | 204 |
| `export_body_property_names` | `GET /api/export/body-properties` | `hostId` only | 200 `text/plain`, one name per line |
| `add_request` | `POST /api/external/ai/requests` | | 201 + `RequestSummaryResponse` |

GETs are retried (connect errors, timeouts, 5xx) up to `AUTOMATA_GET_RETRIES` times with a backoff
of 0.5 s then 1 s. POST and PATCH are never retried.

## Payloads

| Payload | Model (`core/models/`) | Fixture |
|---|---|---|
| `AgentContextDetailedResponse` | `context.AgentContextDetailed` | `agent_context_detailed` |
| `ResolvedTarget` | `context.ResolvedTarget` | `resolved_targets` |
| `HostDetailedResponse` | `context.HostDetailed` | `host_detailed` |
| `AgentContextTenantResponse` | `tenants.AgentContextTenant` | `context_tenants` |
| `ContextRequestSummary` | `context.ContextRequestSummary` | `context_requests_page` |
| `RequestMetadataResponse` | `requests.RequestMetadata` | `request_metadata` |
| `RequestRawResponse` | `requests.RequestRaw` | `request_raw` |
| `RequestSummaryResponse` | `requests.RequestSummary` | `request_summary` |
| `ResponseMetadataDto` | `responses.ResponseMetadata` | `response_metadata` |
| `RawResponseDto` | `responses.ResponseRaw` | `response_raw` |
| `HttpJobSummaryResponseDto` | `jobs.HttpJobSummary` | `job_summary_page` |
| `RequestJobSummaryResponseDto` | `jobs.RequestJobSummary` | `request_job_summary_page` |
| `RequestJobResponseDto` | `jobs.RequestJob` | `request_job` |
| `HttpJobResponseDto` | `jobs.HttpJob` | `job_detailed` |
| `RoutineSummaryResponseDto` / `RoutineDetailedResponseDto` | `reference.RoutineSummary` / `RoutineDetailed` | `routine_summary_page`, `routine_detailed` |
| `VulnerabilitySummaryResponseDto` / `VulnerabilityDetailedResponseDto` | `reference.VulnerabilitySummary` / `VulnerabilityDetailed` | `vulnerability_summary_page`, `vulnerability_detailed` |
| `WordlistSummaryResponseDto` / `WordlistDetailedResponseDto` | `reference.WordlistSummary` / `WordlistDetailed` | `wordlist_summary_page`, `wordlist_detailed` |
| `AgentFindingSummaryResponse` | `findings.AgentFindingSummary` | `finding_summary_page` |
| `AgentFindingDetailedResponse` | `findings.AgentFindingDetailed` | `finding_detailed` |
| `AiJobCreationRequest` (sent) | `jobs.AiJobCreation` | `ai_job_creation` |
| `AiResultRequest` (sent) | `jobs.AiResult` | `ai_result` |
| `AiFindingCreationRequest` (sent) | `findings.AiFindingCreation` | `ai_finding_creation` |
| `FindingPatchRequest` (sent) | `findings.FindingPatch` | `finding_patch` |
| `AiRequestAdditionDto` (sent) | `requests.AiRequestAddition` | `ai_request_addition` |
| `WordlistCreationRequest` (sent, part `metadata`) | `reference.WordlistCreation` | `wordlist_creation` |

The enums are in `core/models/enums.py`. Each accepts a value it does not know and keeps it as is
(its `name` is `UNKNOWN`), so a new value in the core does not break the server.

## Where the core differs from the plan

Found when reading the core's code; the plan was corrected where it mattered.

- `StaticAuthData.lifeTimeInSeconds` is an `Instant` (an ISO timestamp), not a number of seconds.
- `HttpJobResponseDto.genericConfig.matchAndReplace` items are
  `{targetType (HEADER), rule (ADD|REPLACE|DELETE), targetKey, value}`.
- `415 Unsupported Media Type` exists (a request with another content type than expected); the
  plan did not list it. It is reported as a generic refusal with the core's message.
- The core's `staging` moved past the commit the plan was written against (`a3e02b2`); the routes
  and DTOs read here match the plan.

## Checking that the routes still exist

`scripts/check_core_contract.py` compares the routes, HTTP methods and query parameters that
`CoreClient` really sends with the OpenAPI document of the core (`/v3/api-docs`). Run it after any
change in the core's routes, or in the client:

```
uv run python scripts/check_core_contract.py --core-url http://localhost:8080
```

It exits 0 when everything exists, 1 when something is missing and 2 when the document cannot be
read. It does not compare payload shapes; the fixtures and the integration tests cover those.

## Verified against a live core

On 2026-10-27 the integration tests (`tests/integration`, see the plan section 13) ran against the core
built from `staging` at `d7e0297`, with a fresh database. Everything that had only been read in the
code held:

- `states=A,B` (comma-separated) is accepted for `Set<JobState>` (`list_jobs`, `list_request_jobs`).
- The multipart upload of `create_wordlist` works: the `file` part and the JSON `metadata` part (name,
  description, vulnerability ids) are read, the line count and size are right, and the same content
  under another name answers 409.
- A PATCH that leaves fields out leaves them unchanged (`update_finding` with only `severity`).
- The scoped routes answer as documented: a request or job outside the context, or an unknown context,
  is a 404; a job nobody approved cannot be queued (409), has no result yet (409), cannot get an AI
  result (409) and cannot be canceled (409).

## Found against the live core

- **Core bug, fixed:** `GET /api/vulnerabilities/{id}` answered 500 for a vulnerability without a
  parent (`VulnerabilityMapper` called `getParent().getId()` on null), so `get_vulnerability` failed for
  a root class. Fixed in the core by commit `4aa8b20` (`parentId` is null); the integration suite
  checks it (`test_a_root_vulnerability_can_be_read`), which needs a core with that fix.
- A routine whose `configSchema` says `type: object` needs a `custom_config` that is an object: a job
  without one is refused with 400 ("customConfig is not valid for routine ...: $: null found, object
  expected"). The core's message is shown to the agent as it is.
- Host names are validated against real top-level domains (`example.test` is refused, `example.com`
  is accepted) and a host's rate limit must be at least the sum of its long and short limits.
- `POST /api/external/requests` (Burp) answers 201 with no `Location` and no body, so it cannot tell
  the caller the new id; `POST /api/requests/raw` returns the `RequestSummaryResponse` and also takes
  the host id, a tenant and the response. The tests seed through the second.
- The core's `Flyway` migration refuses a database that has tables but no migration history (what an
  old volume of a previous version holds): start the stack with a fresh database volume.
