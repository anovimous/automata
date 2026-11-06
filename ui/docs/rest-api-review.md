# REST API review

Review of the Automata core REST API (`com.automata`, Spring Boot) against this
frontend, brought up to date with the core on 2026-10-29 (the first version of this review
is from 2026-10-23). Every route is listed with the screen that uses it, then the
problems found in the API while doing so. The frontend types in `src/types` mirror the
backend DTO records one to one, the clients are in `src/api`.

## 1. Route → screen

### Reference data

| Route | Screen |
| --- | --- |
| `GET/POST /api/programs`, `GET/PATCH /api/programs/{id}` | Programs list / create, program page, **edit program** dialog |
| `DELETE /api/programs/{id}` | hidden (not implemented, 501) |
| `GET/POST /api/hosts`, `GET/PATCH /api/hosts/{id}` | Hosts of a program (and a global search in the command palette), host page, create host (all three rate limits required), edit host (scope, rate limits, description, technologies, notes; the name is read-only) |
| `DELETE /api/hosts/{id}` | hidden (not implemented, 501) |
| `/api/tenants`, `/api/tenants/{id}` | Tenants of a host, tenant page |
| `/api/tenants/{id}/authentication` (GET/POST/PATCH) | Tenant page, static auth editor (an authentication is only replaced; it goes away with its tenant, there is no delete route) |
| `/api/vulnerabilities` (`query`, `parentVulnId`) | Vulnerabilities list (search) and page (children, parent) |
| `/api/routines` | Routines list / create (protocol and allowed targets required, result content type, config schema, needs-a-wordlist), routine page |
| `DELETE /api/routines/{id}` | hidden (not implemented, 501) |
| `POST /api/wordlists` (multipart), `GET/PATCH/DELETE /api/wordlists/{id}`, `GET /api/wordlists/{id}/content` | Wordlists list, upload dialog (file + name, description, vulnerabilities), wordlist page (name, description, replace vulnerabilities; size, lines, SHA-256; download) |
| `/api/comparators`, `/api/modifiers` | Comparators (with their modifiers) and modifiers lists / pages; a comparator needs at least one available modifier |
| `GET /api/export/body-properties` | "Export properties" on the program and host pages |

### Requests, responses, equalization

| Route | Screen |
| --- | --- |
| `GET /api/requests` | Requests of a host (filters, body size) |
| `GET /api/requests/{id}/metadata`, `/raw`, `/body/stream` | Request page: request line, headers, raw, body preview / download |
| `GET /api/responses/{id}/metadata`, `/raw`, `/body/stream` | Request page, response pane |
| `GET /api/responses` | client + hooks only (no list screen needed) |
| `POST /api/requests/raw`, `POST /api/requests/{id}/response` | Add raw request dialog, "Associate a response" |
| `DELETE /api/requests/{id}`, `DELETE /api/responses/{id}` | hidden (not implemented, 501) |
| `GET/POST /api/requests/equalizations`, `PATCH .../{id}`, `POST .../run` | Host "Equalizations" page |
| `GET/PATCH /api/requests/equality-sets/{id}`, `GET /api/requests/equal` | Equality set page (open by id) |

### Jobs

| Route | Screen |
| --- | --- |
| `GET /api/jobs/http` (state, scope, creator, context, target request filters) | Jobs list, host jobs, request page ("jobs that targeted this request"), context page |
| `GET /api/jobs/http/{id}` | Job page (failure message and category, file status, AI description / result) |
| `POST /api/jobs/http` | Run job form: single host, single request, several requests (NARROW) and several hosts (WIDE, from the host list of a program) |
| `POST .../{id}/queue`, `/retry-preparation`, `/approve`, `/pause`, `/resume`, `/cancel` | Job page buttons (and Approve in the list) |
| `GET .../{id}/result[?inline=true]` | Job page "Result" card, download (FINISHED and FAILED jobs only) |
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

## 2. How the frontend follows the rules of the core

These are rules the core enforces; the forms check them first and show the reason (the
core's answer is shown when it refuses anyway). `src/lib/jobRules.ts` and
`src/lib/hostRules.ts` hold them.

* **Jobs** (`HttpJobCreationService`): a user job needs `rate >= 1` and `priority` 1-5 (the
  form always sends both); the rate must not exceed the host rate limit, the program rate
  limit and the short or long limit of the host for the duration (a wide job: the program
  limit only), otherwise 422. A host with a rate limit of 0 is closed to jobs (422). The routine
  must be available at the consumer and allow the kind of target. A request is a job target only
  when it has no body, or a JSON or FORM body (422 otherwise), and all the requests of a job
  belong to the job's host. The tenant of a narrow job belongs to its host.
* **Selectors**: `{hostId}`, `{requestId}`, `{requestIds}` (several requests, NARROW) and
  `{hostIds}` (several hosts of one program, WIDE).
* **Hosts**: `hostRateLimit`, `shortRateLimit` and `longRateLimit` are required on creation (0 or
  more, `hostRateLimit >= short + long`); a host name is a domain with a dot (a wildcard host is
  entered as `example.com` with scope WILDCARD) and can't be changed. A rate-limit patch is
  refused with 409 while jobs act on the host (non-draft, non-ended jobs of the host, or wide jobs
  of its program); the same goes for `programRateLimit` of a program.
* **Agent contexts**: `maxRate` and `defaultJobRate` are required (at least 1, default at most
  max), `defaultPriority` is 1-5. Deleting a context that jobs or findings still use is a 409.
* **Required fields**: tenant `email`; routine `protocol` and `allowedTargets`; modifier
  `description`, `priority`, `isAvailable`; comparator `modifierIds` (not empty, all available);
  finding `vulnerabilityId` and `evidence`. Missing ones are a 400 listing the violations.
* **Wordlists** are files: the core computes `sha256`, `sizeBytes` and `numberOfLines`, the file
  never changes, a file that already is a wordlist is a 409, deleting one a job uses is a 409.
  `nginx.conf` allows uploads up to 513 MB.
* **Jobs, display**: `terminal` and `waitingForAction` come from the core (not re-derived);
  a job that ended FAILED or SELFTERMINATED carries `failureCategory`; a DRAFT job can be
  canceled; pause and resume are refused (409) while a cancel is pending, so the buttons are
  hidden then.

## 3. Problems that remain in the API

Verified in the code. The frontend works around the ones marked (UI).

### 3.1 Routes that do not work

1. **Deletes** of a program, host, request, routine, response and job answer 501 (not
   implemented). (UI: buttons hidden, flags in `src/config/features.ts`.)
2. **Cancel of a `SCHEDULED` job** is 501, and no route sets `SCHEDULED`. (UI: not offered.)
3. **`PATCH /api/hosts/{id}` can't mark a host out of scope** (`outOfScope` is not in
   `PatchHostRequest`); a description, technologies or notes can be replaced but not set to null.

### 3.2 Missing reads (the UI has to guess or go around)

1. **No route lists equality sets** (of an equalization or of a host), and a request does not say
   which set it is in. The UI can only open a set by id (ids appear on agent context items).
   Suggested: `GET /api/requests/equality-sets?hostId=&equalizationId=` and an `equalitySetId` on
   the request summary.
2. **`POST /api/requests/equalizations` answers 201 without the new id or a `Location`**, and
   `POST /api/requests/equalizations/run` is asynchronous (204 right away).
3. **A comparator's modifiers can't be changed after creation.**
4. **Wordlists do not return their vulnerabilities** (they can be replaced with `vulnIds`).
5. **List summaries are thinner than the detail:** `ModifierSummaryResponse` (no description,
   priority, schema, availability: the UI fetches each detail), `RoutineSummaryResponseDto` (no
   description), `VulnerabilitySummaryResponseDto` (no `parentId`, so the tree cannot be drawn from
   the list), `TenantSummaryResponseDto` (no email).
6. **No `approved` filter and no "AI result missing" filter** on the job list (the second is in
   the TODO).
7. **`GET /api/agent-contexts` without `hostId`** is `findByHostId(null)`, which Spring Data turns
   into `host.id IS NULL`, so the list of all contexts is probably empty (not run; the tests only
   list with a host id).

### 3.3 Contract oddities

1. `StaticAuthData.lifeTimeInSeconds` is an `Instant` (a point in time), not seconds. The UI sends
   an ISO timestamp and labels it "Valid until".
2. `POST /api/programs` binds the JPA entity `Program` as the request body; the out-of-scope
   vulnerabilities cannot be set on create, and `id` / `insertionDate` are accepted from the client.
3. `PATCH /api/requests/equalizations/{id}` takes a bare JSON boolean as the body.
4. Errors are plain-text bodies (`ResponseEntity<String>`). Violations of a request body are
   listed in one text (`email must not be blank; name must not be blank`).
5. `TargetCardinalityPair` has the typo `MULTILPLE_HOSTS` (kept on the wire and in the UI enum).
6. Partial updates rely on `Optional` + `setXNull` flags per field; fields without a flag (for
   example a tenant's email, a routine's allowed targets) cannot be cleared.
7. A user job without a `priority` is not rejected by a rule: the column is NOT NULL, so the core
   answers 400 "A required value is missing". (UI: the priority is required and defaults to 1.)

### 3.4 Job flow detail the UI handles

* A job's config files are built **after** the creating transaction commits; `queue` answers 409
  until `configFileStatus` is `READY`. "Save and queue" therefore polls the job first.
* An AI job needs `approved` before it can be queued (409 otherwise).
* `pause`, `resume` and cancel of a job a worker holds only set `requestedState` (202 / 204); the
  worker applies it on its next poll. The job page shows the pending request.
* States: `SELFTERMINATED` and `LOSTCONTACT` replace `TERMINATED`; the `GLOBAL` scope is gone.

## 4. Not possible with the current API (so not in the UI)

* Scheduling a job.
* Listing / browsing equality sets of a host.
* Changing which modifiers a comparator has.
* Seeing which vulnerabilities a wordlist is linked to.
* Deleting programs, hosts, requests, responses, routines, jobs.
* Marking a host out of scope.
