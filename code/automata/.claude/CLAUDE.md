## Overview

Automata is a Spring Boot 3.5 / Java 21 backend (Maven) for an automation framework aimed at security testing of HTTP APIs: it ingests requests/responses (including from Burp), groups equivalent requests, and schedules "jobs" that external workers poll and execute. Base package: `com.automata`.

## Commands

```bash
./mvnw spring-boot:run                      # run locally (needs the env vars below)
./mvnw package -DskipTests                  # build jar
./mvnw test                                 # all tests
./mvnw test -Dtest=BodyUtilsTest            # one test class
./mvnw test -Dtest=BodyUtilsTest#methodName # one test method
docker compose up -d postgres rabbitmq rustfs rustfs-init   # backing services
docker compose up --build                   # full stack (also builds ../../../automata-frontend)
```

`application.properties` has no defaults: it requires `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD`, `RUSTFS_ENDPOINT/ACCESS_KEY/SECRET_KEY/REGION` and `TMP_FILES_PATH` (see `docker-compose.yml` for values). Optional: `REQUEST_BODIES_BUCKET` / `RESPONSE_BODIES_BUCKET` (default `request-bodies` / `response-bodies`; created and made public by the `rustfs-init` service). `spring.jpa.hibernate.ddl-auto=none` and Flyway is commented out, so schema is not auto-created; there are no migration files in the repo.

## Architecture

Package-by-feature under `src/main/java/com/automata`; each feature typically has `Controller`/`Service`/`Repository`/`Mapper` plus a `common/dto` (and `common/enums`) subpackage. Lombok is used throughout (`@RequiredArgsConstructor` constructor injection).

- **Domain hierarchy**: `tenant` (with `authentication`) → `program` → `host` → `request` → `response`. `routine`, `vulnerability`, `wordlist` are supporting entities. `agentcontext` and `finding` are AI-agent-facing features.
- **request**: ingestion pipeline splits a raw/Burp request into parsed parts via `*Utils` + `*Service` in subpackages (`header`, `path`, `parameter`, `body` — bodies are flattened to `BodyProperty` rows with `json-flattener`). `RequestBurpIntegrationController`/`BurpAdapterService` handle Burp input.
- **Raw storage and bodies**: `RequestRaw`/`ResponseRaw` (lazy, shared id) hold the raw message split into `rawHeadBase64` + nullable `rawBodyBase64`. Any request content type is accepted; detailed `BodyProperty` rows exist only for JSON and FORM requests and JSON responses (a malformed JSON body is rejected). A MULTIPART request body and any non-JSON response body is stored as one file in the `request-bodies` / `response-bodies` bucket (key = id) via `storage.BodyObjectStorage` (deleted again if the transaction rolls back) and flagged with `bodyStoredAsFile`; it is downloaded through `GET /api/{requests,responses}/{id}/body`. Parsing works on bytes (`HttpMessageUtils.split`), never on a decoded string.
- **request/equalityset + comparator**: "equalization" groups requests into `RequestEqualitySet`s per host using a `Comparator` (plus `Modifier`s). Hashing uses Guava murmur3_128 via pluggable `HashStrategy` implementations (method, path, query string, body) resolved by `HashStrategyResolver`. This area is under active refactoring (see `TODO.md`; `BodyHashStrategy.computeLastObjectParent` is flagged as broken).
- **job**: split into `api` (controllers/DTOs, incl. `/api/external/jobs/http` poll/update endpoints used by external workers), `domain` (`HttpJob` → `NarrowHttpJob` (single host/request targets) / `WideHttpJob` (multi-host); embedded selector/matcher/predicate value types; `JobState`), `service`, `infra`, `repository`. AI-specific: `HttpJob` has `aiDescription`, `aiResult`, `createdBy` (`JobCreator.USER`/`AI`), `approved` fields. AI jobs are created via `POST /api/jobs/http/ai` (always DRAFT, `approved=false` by default), approved via `POST /{jobId}/approve`, result set via `POST /{jobId}/ai-result`. Unapproved AI jobs are blocked from queueing.
  - `SpringScheduler` runs `HttpJobsSynchronizationService.syncToQueueJobs()` every 60s in a SERIALIZABLE transaction: sorts pending jobs, enforces program-level and host-level rate limits, publishes to RabbitMQ via `JobQueueService`, then marks jobs `QUEUED`.
  - Job configuration/target data is written as JSON Lines to a temp dir (`TMP_FILES_PATH`, `JobDataTmpStorageService`/`JobDataJsonLinesWriter`) and uploaded to S3-compatible storage (RustFS, buckets `job-configuration` and `job-results`) via `S3Service`; creation of these files is async (`AsyncConfig`, `JobConfigFileAsyncService`).
- **agentcontext**: `AgentContext` scopes an AI agent to a host with a list of `AgentContextItem`s (type `REQUEST` or `EQUALITY_SET`). `resolveContextTargets` resolves items into concrete request IDs with eligibility checks and per-item auto-approval. Two representative strategies: `LATEST` (picks newest request from an equality set) and `AI_JUDGEMENT` (returns all requests for AI to choose). REST at `/api/agent-contexts`.
- **finding**: `Finding` tracks vulnerability findings with CVSS severity (0-10), confidence (0-10), status (`SUGGESTED`/`CONFIRMED`), human review workflow (`TRUE_POSITIVE`/`FALSE_POSITIVE` verdict). Links to request, host (derived), optional job, and vulnerability. PoC reproduction steps required for `CONFIRMED` status. REST at `/api/findings` with filtered pagination.
- **Infra**: PostgreSQL (JPA/Hibernate, `hibernate-types-60` for JSON columns), RabbitMQ (single direct exchange `automataDirectExchange`, JSON converter in `GenericRabbitMQConfig`), Spring Batch/Quartz/Cache starters are present, springdoc OpenAPI UI is enabled. `GlobalExceptionHandler` maps exceptions (e.g. `EntityNotFoundException`) to HTTP responses.

## Worker (automata-worker)

The code that actually runs jobs lives in a separate Go repo, `automata-worker` (`/home/haidar/dev/automata-worker`, `github.com/anovimous/automata-worker`; it has its own `.claude/CLAUDE.md`). The core never executes a tool itself: it prepares and queues jobs, and an **orchestrator** process from that repo picks them up, runs the matching **connector** (`ffuf-connector`, `masser-connector`) and reports back. Core and worker only talk through these contracts, so a change to one side must be checked against the other. The full contract (payload fields, status codes, state tables) is `docs/worker-contract.md`; tests in `src/test/java/com/automata/contract` check it against the code, and the worker repo parses the same samples (`src/test/resources/contract` here, `contract/samples` there, kept equal by hand). Change a payload, a state rule or the doc together with those:

- **Queue**: `JobQueueService` publishes `HttpJobQueueMetadata(jobId, routineKey)` (JSON `{jobId, routineKey}`) with routing key `jobs.http.ready` to queue `jobs.http.queue` (`x-max-priority` 2; priority 2 for eager jobs, otherwise 1). The orchestrator declares the same queue from its `RABBITMQ_QUEUE` env and strictly (a mismatch fails at startup); the constants `HttpJobRabbitMQConfig.QUEUE/ROUTING_KEY/MAX_PRIORITY` must stay in line with `queueArguments()` in the worker's `queue/consumer.go`.
- **Object storage**: the orchestrator reads `details/{jobId}.json` (`JobDetailsFileContainer`: rate, verbosity, auth, targetType, genericConfig, customConfig, wordlistsPaths) and `data/{jobId}.jsonl` (one `RequestData` or `HostData` per line) from the `job-configuration` bucket and writes `{jobId}.result` to `job-results`, which `GET /api/jobs/http/{id}/result` serves.
- **HTTP**: the orchestrator polls `GET /api/external/jobs/http/poll/{jobId}` (`JobPollDto{requestedState}`, falling back to the current state when nothing is requested) and reports with `POST /api/external/jobs/http/update/{jobId}` (`JobStateUpdateRequest{jobId,newState,message}`, answered with 204).
- **Routines**: a `Routine.key` must have an entry in the worker's `routine-worker-mapping.json` (`fuzz` → ffuf, `masser` → masser), otherwise the job fails at the worker. `customConfig` is opaque to the core; only the connector of that routine parses it (ffuf: `rawRequestBase64` + `flags`; masser: `flags`).
- **States**: the core owns the state machine (`JobStateTransitions`). A worker may only report `QUEUED`→`RUNNING|CANCELED|FAILED|SELFTERMINATED`, `RUNNING`→`PAUSED|CANCELED|FINISHED|FAILED|SELFTERMINATED`, `PAUSED`→`RUNNING|CANCELED|SELFTERMINATED`, and `PAUSED`/`CANCELED`/resume only when the user requested them. The user's pause/resume/cancel is a *requested* state the worker picks up by polling. `SELFTERMINATED` is what a worker reports when it ends a job itself. `LOSTCONTACT` is set only by the core: every poll and accepted report sets `lastWorkerContactAt`, and a scheduled task moves `RUNNING`/`PAUSED` jobs without contact for 30 minutes to `LOSTCONTACT` (a worker can't report it, and the orchestrator stops the tool when the poll answers it).
- **Wordlists**: `Wordlist.path` is passed to the worker as-is, so core and worker currently have to see the same filesystem paths.

Known problems of this integration (stuck jobs when an orchestrator dies, cancel/failure reporting mismatches, public buckets, no authentication, ...) are written down in `.claude/components-code-review.md`.

## Repo notes

- `.claude/settings.json` denies `git push` for Claude; leave pushing to the user.
- Tests live in `src/test/java` (JUnit via spring-boot-starter-test)
- Any change to the core/worker contract (queue, payloads, status codes, states, transition rules) must be reflected in `docs/worker-contract.md` in the same change, with the samples and tests that check it (and the worker copy of the samples)
- Don't add yourself as a coauthor
- Do not commit anything until I confirm
- Merges are only allowed when I explicitly say so, if merging always use --no-ff
- Branches should only be created from staging
- Flow is when allowed to commit and merge : branch from staging -> do the work and commit the changes -> switch to staging > merge the branch
- When commiting, split them into several commits if it is better this way
- Branch names are `<type>/<slug>` and the slug must contain a verb saying what was done, not only the object: `refactor/remove-global-job-scope`, `fix/apply-job-list-filter`, `feat/allow-cancel-running-job`; not `fix/job-list-filter` or `feat/lost-contact-state`

## Coding notes

- If I didn't explicitly tell you to start coding then don't start
- Write unit test cases for each new method which you implement, and for each method which you modify