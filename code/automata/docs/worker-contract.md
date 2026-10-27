# Contract between the core and the worker

What the core (`automata`) and the worker (`automata-worker`: orchestrator, connectors, SDK) agree on. It was written from the code of both repos, not from memory, and the parts that can be checked by a test are:

- **JSON shapes**: the core's tests (`src/test/java/com/automata/contract`) write each payload with the application's own code and Jackson set-up and compare it with the samples in `src/test/resources/contract`. The worker keeps a copy of the same samples in `contract/samples` and parses them with its own types (parser, queue, poller, informer, both connector builders). A renamed field fails one side's test.
- **State tables** below: `StateTableContractTest` computes them from `JobStateTransitions` and compares them with this file, so edit the tables only together with the code.

The two sample directories must be kept equal by hand (`diff -r src/test/resources/contract ../automata-worker/contract/samples`). Whatever is marked **not checked** is described from reading the code only.

## 1. Flow of a job

1. The user creates a draft job. The core builds `details/{id}.json` and `data/{id}.jsonl` in the `job-configuration` bucket asynchronously (`configFileStatus` PREPARING → READY).
2. The user prepares it (`DRAFT` → `TOQUEUE`). Every 60 s the scheduler picks the `TOQUEUE` jobs that fit the rate limits, publishes one queue message per job and sets them `QUEUED`. The publish happens inside the same transaction as the state change, on purpose: delivery is at-least-once, duplicates are accepted.
3. An orchestrator takes one message when it is idle, creates `TEMP_DIR/job-{id}`, downloads the two files, polls once, starts the connector for the routine, reports `RUNNING` and only then acks the message.
4. While the job runs the orchestrator polls the core, forwards what the user requested (pause, resume, cancel) to the connector over a unix socket, and reports every state the connector reports.
5. At the end the orchestrator uploads the result (only for `FINISHED`) and reports the final state. The user downloads the result from the core.

## 2. Queue (RabbitMQ)

| | |
|---|---|
| Exchange | `automataDirectExchange`, direct (declared by the core) |
| Queue | `jobs.http.queue`, durable, argument `x-max-priority` = 2 (declared by the core **and** by every orchestrator; the broker refuses a declaration that differs, so a mismatch stops the orchestrator at startup) |
| Binding / routing key | `jobs.http.ready` |
| Message | persistent, `application/json`, body `{"jobId": 42, "routineKey": "fuzz"}` |
| Priority | 2 for an eager job, 1 otherwise |
| Worker side | `RABBITMQ_QUEUE` must name this queue. The orchestrator pulls one message at a time (`basic.get`), only when idle, and acks it after it reported `RUNNING` or when it needs no more attempts. A crash before that redelivers it. |

The core's constants are `HttpJobRabbitMQConfig.QUEUE / ROUTING_KEY / MAX_PRIORITY`; the worker's are `queueDurable` and `queueArguments()` in `orchestrator/internal/queue/consumer.go`. Change both together.

The orchestrator ignores the message properties and reads only the body. `routineKey` selects the connector through the worker's `routine-worker-mapping.json` (`fuzz` → `ffuf-connector`, `masser` → `masser-connector`); an unmapped key makes the orchestrator report `FAILED`.

## 3. Object storage (S3 API, RustFS)

The core writes with its own credentials. The orchestrator uses `RUSTFS_JOBS_BUCKET`, `RUSTFS_RESULTS_BUCKET` and `RUSTFS_WORDLISTS_BUCKET`, which must be `job-configuration`, `job-results` and `wordlists` (the core's `WORDLISTS_BUCKET`).

| Object | Written by | Read by |
|---|---|---|
| `job-configuration/details/{jobId}.json` | core | orchestrator → connector |
| `job-configuration/data/{jobId}.jsonl` | core | orchestrator downloads it, only masser reads it |
| `job-results/{jobId}.result` | orchestrator | core (`GET /api/jobs/http/{id}/result`) |
| `wordlists/{wordlistId}` | core (on `POST /api/wordlists`) | orchestrator, see 3.5 |

### 3.1 Details file

The core writes it with the application's Jackson mapper (`FAIL_ON_UNKNOWN_PROPERTIES` off, `WRITE_DATES_AS_TIMESTAMPS` off); a `null` is written as `null`. Samples: `details-file.json` (with authentication) and `details-file-without-auth.json`.

| Field | JSON | Notes |
|---|---|---|
| `jobId` | number | |
| `routineKey` | string | |
| `rate` | number or `null` | requests per second; the worker reads `null` as 0 |
| `verbosity` | `SUMMARIZED` \| `NORMAL` \| `VERBOSE` | masser gets it as `-verbosity`; ffuf ignores it |
| `wordlists` | array of `{id, name, sha256, sizeBytes}` | ordered by `id`; may be empty; `name` may be `null`. The orchestrator downloads each one (3.5) and gives the connectors local paths, see below. ffuf needs exactly one and reports a job with none or more than one as `FAILED` (`INVALID_CONFIG`) (masser takes all of them) |
| `auth` | object or `null` | only narrow jobs of a tenant with stored authentication; the worker treats `null` as no headers, cookies or query parameters |
| `auth.data.headers`, `.cookies`, `.queryParameters` | arrays of `{"key", "value"}` | the worker uses headers and cookies; **query parameters are never applied by either connector** |
| `auth.lifeTimeInSeconds`, `auth.populatedAt` | ISO-8601 instant strings | the worker keeps them as strings and never reads them |
| `targetType` | `REQUEST` \| `HOST` | `HOST` for single/multiple host selectors; the worker does not read it |
| `genericConfig.matchAndReplace` | array of `{targetType, rule, targetKey, value}` | `targetType` is `HEADER` only; `rule` is `ADD` \| `REPLACE` \| `DELETE` |
| `customConfig` | any JSON, or `null` | checked by the core against the routine's `configSchema` when the job is created, see 3.3 |

The file holds credentials (`auth`). The `job-configuration` and `job-results` buckets are public today (known, see the review file); the `wordlists` bucket is private.

The connectors never see `wordlists`: before it starts one, the orchestrator rewrites its copy of the file in the job directory so that it has `wordlistsPaths` (an array of absolute local paths, in the order of `wordlists`) instead of `wordlists`. Everything else in the file is kept as it is. The worker's own sample of that form is `details-file-resolved.json`.

Match-and-replace as the worker applies it: for `HEADER` and a rule other than `DELETE` it adds the header `targetKey: value`. `ADD` and `REPLACE` therefore behave the same (they do not replace an existing header of the request), and **`DELETE` is accepted by the core and ignored by both connectors**.

### 3.2 Data file

JSON Lines, UTF-8, one object per line, written with a plain `ObjectMapper`. Samples: `data-requests.jsonl`, `data-hosts.jsonl`.

| Job kind | Line | Notes |
|---|---|---|
| Narrow job on requests | `{"requestId": 7, "base64Request": "..."}` | the raw HTTP request (request line, headers, body) encoded as UTF-8 text and then Base64; this re-encoding of the bytes as text is a known deviation of the bytes-only rule |
| Narrow job on one host, wide job | `{"hostId": 3, "host": "example.com", "scope": "FQDN"}` | `scope` is `FQDN` or `WILDCARD` |

The worker never parses this file: the orchestrator downloads it and the connector passes its path to the tool (`masser -requests <file>`). ffuf does not read it at all; it takes its request from `customConfig.rawRequestBase64`. **Not checked:** that `masser` reads exactly these line shapes (its source is not part of either repo).

### 3.3 `customConfig` per routine

The core checks it when a job is created (narrow, wide and AI jobs): if the routine has a `configSchema` (JSON Schema, draft 2020-12, given when the routine is created or patched) the `customConfig` has to satisfy it, otherwise the creation is answered with 400 and the list of violations. A routine without a `configSchema` is not checked. **The flag enums of the schema of `fuzz` and `masser` have to equal `allowedFlags` of the worker connectors; nothing checks this automatically, so change them together.**

The connector of the routine is still the one that parses it; both ignore unknown fields and reject a flag outside their allow-list (`Unknown flag provided in custom config`). A `customConfig` that can not be used (wrong shape, unknown flag, a request that is not Base64) makes the connector report the job `FAILED` with the reason.

| Routine | Shape | Becomes |
|---|---|---|
| `fuzz` (ffuf) | `{"rawRequestBase64": "<base64>", "flags": [{"key": "-mc", "value": "200"}]}` | `-request <file in the job dir>` (the decoded bytes) and `<key> <value>` for each flag |
| `masser` | `{"flags": [{"key": "-n", "value": "5"}]}` | `<key> <value>` for each flag |

Always passed by the connector: ffuf `-o <result> -w <the wordlists path> -rate <rate> -noninteractive`, `-b` for cookies, `-H` for headers; masser `-requests <data file> -output <result> -rate <rate>`, `-verbosity`, one `-wordlist` per wordlist, `-C` for cookies, `-H` for headers. The allow-lists are `allowedFlags` in each `builder.go`.

### 3.4 Result

`{jobId}.result` is whatever file the tool wrote, uploaded unchanged. Only a `FINISHED` job has one. A `FAILED`, `CANCELED`, `SELFTERMINATED` or `LOSTCONTACT` job has none; the reason of a failure is stored with the job (`failureMsg`, cut to 500 characters, with its `failureCategory`).

### 3.5 Wordlists

A wordlist is created by `POST /api/wordlists` (multipart: part `file`, at most 512 MB, and an optional JSON part `metadata` with `name`, `description`, `vulnIds`). The core reads the file once and stores its SHA-256 (lower case hex), size in bytes and number of lines, and uploads it to the `wordlists` bucket under the key `{wordlistId}`. The file of a wordlist never changes (no route replaces it), so an object is the content `sha256` says for as long as the wordlist exists; a wordlist with the same file as an existing one is refused (409). The core refuses to delete a wordlist that a job refers to (409).

The orchestrator, after the initial poll says the job may start and before it starts the connector:

1. takes the `wordlists` of the details file;
2. for each one, uses the cached file `<WORDLIST_CACHE_DIR>/<sha256>` when it exists with `sizeBytes` bytes, otherwise downloads `wordlists/{id}`, checks the SHA-256 and the size, and keeps it under that name (written to a temporary name and renamed, so a half file is never used);
3. keeps the files of the job in the cache while the job runs, and removes the least recently used other files while the cache is over `WORDLIST_CACHE_MAX_BYTES` (1.5 GB by default; one job whose wordlists alone are bigger still runs). There is no time limit on cached files;
4. rewrites the details file as described in 3.1.

A wordlist that cannot be downloaded, or does not match its `sha256` or `sizeBytes`, fails the job as `PREPARATION` (the message names the wordlist). The cache key is the hash, so a cached file is never stale and no check against the core is needed.

## 4. HTTP

Both endpoints are under `/api/external/jobs/http`. There is no authentication.

### 4.1 Poll: `GET /poll/{jobId}`

Answer 200 `{"requestedState": "PAUSED"}` (sample `poll-response.json`). `requestedState` is the state the user asked for, or the job's current state when nothing is requested, so every state name can come back, including `QUEUED` and `LOSTCONTACT`. Every poll records `lastWorkerContactAt`. An unknown job answers 404.

Worker: the first poll before starting decides what to do (`QUEUED` → start, `CANCELED` → report `CANCELED`, anything else → the message is stale or a duplicate, skip without a report; a failed poll → report `FAILED`). While the job runs the poller sends a state to the connector only when it differs from the last one it sent; a failed poll is skipped silently. `LOSTCONTACT` makes the orchestrator terminate the tool without reporting.

### 4.2 Report: `POST /update/{jobId}`

Body (sample `update-request.json`): `{"jobId": 42, "newState": "FAILED", "message": "...", "failureCategory": "INVALID_CONFIG"}`. The job is the one in the path; `jobId` in the body is ignored. `message` is kept (cut to 500 characters) as the failure message, and `failureCategory` as the failure category, only for `FAILED` and `SELFTERMINATED`, and only the first time. `failureCategory` is optional: it is sent with a failure only and left out of every other report (the worker omits it when empty).

`failureCategory` says what kind of failure it was, so a reader knows what to do next; the message says what happened. The core reads a name it does not know, or a failure reported without a category, as `UNKNOWN` (stored and returned as `failureCategory` of the job responses; a job that did not fail has none).

| Category | Reported by | When |
|---|---|---|
| `PREPARATION` | orchestrator | the work directory could not be created, the details and data files could not be downloaded, a wordlist could not be downloaded or did not match its hash, or the routine key has no mapping |
| `POLL` | orchestrator | the first poll of the core failed |
| `INVALID_CONFIG` | connector | the job details could not be parsed or turned into a command (unknown flag, not exactly one wordlist for ffuf, a `customConfig` the connector can not read) |
| `TOOL_START` | orchestrator, connector | the connector or the tool could not be started, or the connector did not connect to the sockets in time |
| `TOOL_EXIT` | connector | the tool ran and exited with an error |
| `RESULT` | orchestrator | the job finished without a result file, the file is missing, or it could not be uploaded |
| `UNKNOWN` | orchestrator, core | nothing more is known: a worker that reports no category, a heartbeat timeout, or a failure the core records itself (`LOSTCONTACT`) |

| Status | When | Worker |
|---|---|---|
| 204 | accepted (a repeated report of the current state, except `RUNNING`, is accepted and changes nothing) | done |
| 400 | the body can not be read: not JSON, or a state name the core does not have (such as the old `TERMINATED`) | not retried, logged and dropped |
| 404 | unknown job | not retried |
| 409 | the move is not allowed (section 5), or the job was changed at the same time (optimistic lock) | not retried, logged and dropped |
| 5xx | any other error | retried: 5 attempts, 1 s doubling up to 30 s, 10 s timeout per request |
| network error / timeout | | retried the same way |

The 400 is not specific to this endpoint: `GlobalExceptionHandler` maps `HttpMessageNotReadableException` to 400 for every endpoint of the core.

A report is accepted only for the moves in section 5. An accepted report sets `currentState`, clears `requestedState` and records `lastWorkerContactAt`.

## 5. States

<!-- states list, checked by a test: one line per state of JobState, in order -->

- `DRAFT` — created, files being prepared or ready
- `TOQUEUE` — prepared by the user, waiting for the scheduler
- `SCHEDULED` — reserved, never assigned by the code today
- `QUEUED` — message published, no worker has started it
- `RUNNING` — a worker reported it started
- `PAUSED` — the worker paused it on the user's request
- `CANCELED` — the user canceled it (the worker confirmed it, unless it was not yet queued)
- `FINISHED` — ended with a result
- `FAILED` — ended with an error, the message says why
- `SELFTERMINATED` — the worker ended the job itself (timeout, connector crash)
- `LOSTCONTACT` — set by the core when no worker was heard about a `RUNNING`/`PAUSED` job for 30 minutes (`HttpJobStateService.LOST_CONTACT_AFTER`)

### 5.1 What a worker may report

Anything not in the table is answered 409. The states `DRAFT`, `TOQUEUE`, `SCHEDULED`, `QUEUED` and `LOSTCONTACT` can never be reported. "Needs a user request" means the job's `requestedState` must already be the reported state.

<!-- worker-reports:start -->
| From | Reports | Needs a user request |
|---|---|---|
| QUEUED | RUNNING | no |
| QUEUED | CANCELED | yes |
| QUEUED | FAILED | no |
| QUEUED | SELFTERMINATED | no |
| RUNNING | PAUSED | yes |
| RUNNING | CANCELED | yes |
| RUNNING | FINISHED | no |
| RUNNING | FAILED | no |
| RUNNING | SELFTERMINATED | no |
| PAUSED | RUNNING | yes |
| PAUSED | CANCELED | yes |
| PAUSED | SELFTERMINATED | no |
<!-- worker-reports:end -->

### 5.2 What the user and the core do

The user only changes `currentState` while no worker holds the job (prepare, cancel before queueing); otherwise the user sets `requestedState` and the worker carries it out the next time it polls. `-` means the field becomes empty. Endpoints are under `/api/jobs/http`.

<!-- actions:start -->
| Action | From | Current becomes | Requested becomes |
|---|---|---|---|
| prepare | DRAFT | TOQUEUE | - |
| pause | RUNNING | RUNNING | PAUSED |
| resume | PAUSED | PAUSED | RUNNING |
| cancel | TOQUEUE | CANCELED | - |
| cancel | QUEUED | QUEUED | CANCELED |
| cancel | RUNNING | RUNNING | CANCELED |
| cancel | PAUSED | PAUSED | CANCELED |
| lose contact | RUNNING | LOSTCONTACT | - |
| lose contact | PAUSED | LOSTCONTACT | - |
<!-- actions:end -->

Also: the scheduler moves `TOQUEUE` → `QUEUED` when it publishes the message (a bulk update in `HttpJobsSynchronizationService`, not in `JobStateTransitions`); canceling a `SCHEDULED` job answers 501; the user endpoints are `queue`, `pause`, `resume`, `cancel`.

## 6. Delivery and failure rules

- Delivery is at-least-once. A job can be delivered twice; the first poll protects against starting it twice (only a job still `QUEUED` is started). A repeated `RUNNING` report is rejected with 409 and logged.
- If the orchestrator cannot report a state after the retries, the report is dropped and logged. A `RUNNING`/`PAUSED` job without any contact for 30 minutes becomes `LOSTCONTACT`; a `QUEUED` job is never reaped.
- A `FINISHED` report is sent only after the result was uploaded (with retries); if there is no result file or the upload fails, `FAILED` is reported instead.
- The orchestrator runs one job at a time, in `TEMP_DIR/job-{id}`, which is removed when the job is over (it holds the details file, with credentials).

## 7. Known gaps of the contract

Not changed here, listed so nobody assumes otherwise (details in `.claude/components-code-review.md`):

- The `wordlists` bucket is private but the worker holds full RustFS credentials; a user cancel during a wordlist download is only applied by the first poll after the job has started.
- No version field in any payload; compatibility is only checked by the sample tests.
- `auth.data.queryParameters` and the match-and-replace `DELETE` rule are not applied by the connectors.
- Buckets are public and the HTTP endpoints have no authentication.
