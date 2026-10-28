# REST API review

Review of the Automata core REST API (`com.automata`, Spring Boot) against this
frontend, 2026-10-23. Every route is listed with the screen that uses it, then the
problems found in the API while doing so. The frontend types in `src/types` mirror the
backend DTO records one to one, the clients are in `src/api`.

## 1. Route → screen

### Reference data

| Route | Screen |
| --- | --- |
| `GET/POST /api/programs`, `GET/PATCH /api/programs/{id}` | Programs list / create, program page, **edit program** dialog |
| `DELETE /api/programs/{id}` | hidden (not implemented, 500) |
| `GET/POST /api/hosts`, `GET/PATCH /api/hosts/{id}` | Hosts of a program, host page, edit host (incl. description, technologies, notes) |
| `DELETE /api/hosts/{id}` | hidden (not implemented, 500) |
| `/api/tenants`, `/api/tenants/{id}` | Tenants of a host, tenant page |
| `/api/tenants/{id}/authentication` (GET/POST/PATCH) | Tenant page, static auth editor |
| `DELETE /api/tenants/{id}/authentication` | hidden (broken, see 2.1) |
| `/api/vulnerabilities` (`query`, `parentVulnId`) | Vulnerabilities list (search) and page (children, parent) |
| `/api/routines` | Routines list / create (result content type, config schema), routine page |
| `DELETE /api/routines/{id}` | hidden (not implemented, 500) |
| `/api/wordlists` | Wordlists list / create (several vulnerabilities), wordlist page |
| `/api/comparators`, `/api/modifiers` | Comparators and modifiers lists / pages |
| `GET /api/export/body-properties` | "Export properties" on the program and host pages |

### Requests, responses, equalization

| Route | Screen |
| --- | --- |
| `GET /api/requests` | Requests of a host (filters, body size) |
| `GET /api/requests/{id}/metadata`, `/raw`, `/body/stream` | Request page: request line, headers, raw, body preview / download |
| `GET /api/responses/{id}/metadata`, `/raw`, `/body/stream` | Request page, response pane |
| `GET /api/responses` | client + hooks only (no list screen needed) |
| `POST /api/requests/raw`, `POST /api/requests/{id}/response` | Add raw request dialog, "Associate a response" |
| `DELETE /api/requests/{id}`, `DELETE /api/responses/{id}` | hidden (500 / silent no-op) |
| `GET/POST /api/requests/equalizations`, `PATCH .../{id}`, `POST .../run` | Host "Equalizations" page |
| `GET/PATCH /api/requests/equality-sets/{id}`, `GET /api/requests/equal` | Equality set page (open by id) |

### Jobs

| Route | Screen |
| --- | --- |
| `GET /api/jobs/http` (state, scope, creator, context, target request filters) | Jobs list, host jobs, request page ("jobs that targeted this request"), context page |
| `GET /api/jobs/http/{id}` | Job page (failure message, file status, AI description / result) |
| `POST /api/jobs/http` | Run job form (single host, single request) |
| `POST .../{id}/queue`, `/retry-preparation`, `/approve`, `/pause`, `/resume`, `/cancel` | Job page buttons (and Approve in the list) |
| `GET .../{id}/result[?inline=true]` | Job page "Result" card, download |
| `DELETE .../{id}` | hidden (501) |

### Findings and agent contexts (new sections)

| Route | Screen |
| --- | --- |
| `/api/findings` (GET, POST, PATCH, DELETE, `POST .../{id}/review`) | Findings list, finding page (edit, review), "Report finding" |
| `/api/agent-contexts` (GET, POST, PATCH, DELETE) | Agent contexts list / create, context page |
| `POST/DELETE .../{id}/items`, `.../{id}/tenants` | Context page, "Add to agent context" on request / equality set pages |
| `GET .../{id}/resolved-requests` | Context page, "Resolved requests" |

### No UI on purpose

Routes only the AI agent (through the MCP server), Burp or the workers call:
`/api/agent-contexts/uuid/**` (reads, scoped job operations), `GET /api/agent-contexts/uuid/{uuid}`,
`POST /api/external/ai/requests`, `POST /api/jobs/http/{id}/ai-result`,
`POST /api/external/requests` (Burp), `/api/external/jobs/http/poll|update` (workers).

## 2. Problems found in the API

Verified in the code. The frontend works around the ones marked (UI).

### 2.1 Routes that do not work

1. **`DELETE /api/tenants/{tenantId}/authentication`** declares `@PathVariable Long authenticationId`
   that is not in the path, so Spring fails the call. The service only needs the tenant id.
   (UI: delete button hidden, `DELETE_TENANT_AUTH_BACKEND_READY`.)
2. **Deletes that throw `RuntimeException("... not implemented")`** (answered 500): program, host,
   request, routine. Job delete throws `UnsupportedOperationException` (501). **Response delete is an
   empty method** and answers 204 without deleting. Use 501 for all of them, or implement them.
   (UI: buttons hidden.)
3. **`PATCH /api/hosts/{id}` ignores the rate limits**: they are validated and the code that stores them
   is commented out. `outOfScope` cannot be patched at all. The host format is validated on the
   *old* value (`host.getHost()`), not on `patchRequest.host()`. (UI: rate fields disabled.)
4. **`PATCH /api/programs/{id}` ignores `programRateLimit`** (commented out). (UI: field disabled.)
5. **`POST /api/jobs/http` with a `MULTIPLE_HOSTS` / `MULTIPLE_REQUESTS` selector** answers 501, so WIDE
   jobs and jobs on several requests cannot be created. (UI: flagged, submit blocked.)
6. **Cancel of a `SCHEDULED` job** is 501, and no route sets `SCHEDULED`.

### 2.2 Missing reads (the UI has to guess or go around)

1. **No route lists equality sets** (of an equalization or of a host), and a request does not say which
   set it is in. The UI can only open a set by id (ids appear on agent context items).
   Suggested: `GET /api/requests/equality-sets?hostId=&equalizationId=` and an `equalitySetId` on the
   request summary.
2. **`POST /api/requests/equalizations` answers 201 without the new id or a `Location`.**
3. **Comparators do not return their modifiers**, and a comparator's modifiers cannot be changed after
   creation. `ComparatorResponse` is `{id, creationDate, name, schema}`.
4. **Wordlists do not return their vulnerabilities** (they can be set on create / patch), and the list
   summary has no `path`.
5. **List summaries are thinner than the detail:** `ModifierSummaryResponse` (no description, priority,
   schema, availability: the UI fetches each detail), `RoutineSummaryResponseDto` (no description),
   `VulnerabilitySummaryResponseDto` (no `parentId`, so the tree cannot be drawn from the list),
   `TenantSummaryResponseDto` (no email).
6. **`GET /api/hosts` requires `programId`** (the `programId == null` branch is dead), so there is no
   global host search; the command palette only searches the hosts of the first matching program.
7. **No `approved` filter and no "AI result missing" filter** on the job list (the second is in the TODO).

### 2.3 Contract oddities

1. `StaticAuthData.lifeTimeInSeconds` is an `Instant` (a point in time), not seconds. The UI sends an
   ISO timestamp and labels it "Valid until".
2. `POST /api/programs` binds the JPA entity `Program` as the request body; the out-of-scope
   vulnerabilities cannot be set on create, and `id` / `insertionDate` are accepted from the client.
3. `PATCH /api/requests/equalizations/{id}` takes a bare JSON boolean as the body.
4. `FindingCreationRequest.vulnerabilityId` is required by the service but nothing validates it before
   `findById(null)`; `createdBy` is chosen by the caller.
5. Errors are plain-text bodies (`ResponseEntity<String>`), and `Exception.getMessage()` of unexpected
   errors is returned as the 500 body. Bean validation (`@Valid`) is not used anywhere. An unknown `sort` property is not mapped to a
   400 either; it falls through to the generic 500 handler.
6. `TargetCardinalityPair` has the typo `MULTILPLE_HOSTS` (kept on the wire and in the UI enum).
7. Partial updates rely on `Optional` + `setXNull` flags per field; fields without a flag (for example a
   tenant's email, a routine's allowed targets) cannot be cleared.

### 2.4 Job flow detail the UI handles

* A job's config files are built **after** the creating transaction commits; `queue` answers 409 until
  `configFileStatus` is `READY`. "Save and queue" therefore polls the job first.
* An AI job needs `approved` before it can be queued (409 otherwise).
* `pause`, `resume` and cancel of a job a worker holds only set `requestedState` (202); the worker
  applies it on its next poll. The job page shows the pending request.
* New states `SELFTERMINATED` and `LOSTCONTACT` replace `TERMINATED`; the `GLOBAL` scope is gone.

## 3. Not possible with the current API (so not in the UI)

* Scheduling a job; WIDE jobs; jobs on several requests.
* Listing / browsing equality sets of a host.
* Seeing or editing which modifiers a comparator has.
* Seeing which vulnerabilities a wordlist is linked to.
* Deleting programs, hosts, requests, responses, routines, jobs; deleting tenant auth.
* Changing host / program rate limits, or marking a host out of scope.
