# Automata

A centralized framework for managing and scaling offensive security testing operations, built around low-level HTTP request/response attack surface management. Automata acts as the operational backbone for a pentester's workflow — centralizing target tracking, request storage, job orchestration, and rate governance in one place.

---

## Additional Components

**Frontend**: github.com/anovimous/automata-frontend

**Orchestrator**: github.com/anovimous/orchestrator

## Overview

Automata addresses the operational complexity of running large-scale, multi-target security assessments. Rather than running tools ad-hoc, it gives you:

- A **centralized store** of target programs, hosts, tenants, and HTTP requests captured at the parameter level
- A **job system** with scheduling, queuing, priority, and fine-grained rate limiting — per program and per host, with separate pools for short- and long-running jobs
- A **Burp Suite extension integration** to pipe requests from your proxy directly into the store
- A **loosely coupled orchestrator model** where external Go-based worker processes consume jobs from a RabbitMQ queue, execute tools, and write results back — horizontally scalable without modifying the core service
- **Full job lifecycle control**: pause, resume, and cancel across distributed workers

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Automata Core (Spring Boot)           │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐  │
│  │ Programs │  │  Hosts   │  │  Requests / Responses │  │
│  └──────────┘  └──────────┘  └──────────────────────┘  │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │              Job Engine                         │    │
│  │  Scheduling · Queuing · Rate Governance         │    │
│  └───────────────────┬─────────────────────────────┘    │
└──────────────────────│──────────────────────────────────┘
                       │ RabbitMQ
         ┌─────────────▼──────────────┐
         │   Go Orchestrator Workers  │  (horizontally scalable)
         │   (unified SDK + connectors│
         │    for CLI-based tools)    │
         └─────────────┬──────────────┘
                       │
               ┌───────▼───────┐
               │ RustFS (S3)   │  ← job results & config files
               └───────────────┘

Persistence: PostgreSQL
```

The core service exposes a REST API consumed by the frontend UI and the Burp Suite extension. Workers communicate exclusively through RabbitMQ and the external job integration endpoints (`/api/external/jobs/http`). Workers are never coupled to the core service's internals.

---

## Domain Model

### Programs, Hosts & Tenants

The attack surface is organized in a three-level hierarchy:

**Program** → **Host** → **Tenant**

- A **Program** represents a bug bounty or pentest engagement (e.g. a HackerOne/Bugcrowd/Intigriti program). It holds a program-level rate limit and a set of out-of-scope vulnerabilities.
- A **Host** is a target domain or IP under a program (wildcard or FQDN). Each host carries three independent rate limits:
  - `hostRateLimit` — overall cap for the host
  - `shortRateLimit` — cap for the short-running job pool
  - `longRateLimit` — cap for the long-running job pool
  - Hosts also track their `level` (0 = root domain, incremented for subdomains) and `scope` (`NARROW`/`WILDCARD`)
- A **Tenant** is an authenticated identity on a host — the persona the scanner acts as. Each tenant has an optional `Authentication` object holding:
  - `StaticAuthData` — static credentials or header values used as-is
  - `DynamicCode` — a script/command invoked at runtime to generate fresh tokens

### Requests & Responses

Requests are the most important entity in Automata. They are stored at a deep level of granularity:

- **Method**, computed path, HTTP version, content type, source (`MANUAL` or `WAYBACK`)
- **Query parameters** — each parameter stored individually with key/value and analysis metadata
- **Path variables** — extracted and stored per segment
- **Body properties** — for JSON and form-encoded bodies, each leaf property is stored individually (key, value, depth, parent path), enabling fine-grained attack surface targeting
- An optional linked **Response** (status code, headers, body, content type, body properties)

This decomposition is what makes reproducible, parameter-level testing possible.

### Equality Sets & Comparators (STILL IN DEVELOPMENT)

Requests can be **equalized** — deduplicated into `RequestEqualitySet` groups representing functionally equivalent endpoints. This is useful for reducing noise when the same logical endpoint appears multiple times with different parameter values.

Equalization is driven by a **Comparator**, which is a named rule set composed of **Modifiers**. Each Modifier targets a specific part of the request (method, path, query string, body) and applies a hash strategy to determine equality. Comparators can be scoped globally, to a specific program, or to a specific host.

Equality sets are first-class job targets — you can start a job on an equality set the same way you start one on a raw request.

### Routines & Vulnerabilities

A **Routine** is a reusable testing playbook. It defines:

- A unique `key` used to identify it in the mappings file and by workers
- The `overhead` class: `HIGH`, `MEDIUM`, or `LOW`
- The `protocol` it operates on (currently `HTTP`)
- A set of `allowedTargets` — the `TargetCardinalityPair` values describing which selector types this routine supports (single request, multiple requests, single host, etc.)
- An optional linked **Vulnerability** from the taxonomy tree

**Vulnerabilities** form a hierarchical taxonomy (`parent` self-reference), letting you organize findings under categories (e.g. Injection → SQL Injection → Time-Based).

### Wordlists

A **Wordlist** is a registry entry pointing to a wordlist file on the worker's filesystem. It stores the file path, a human-readable name, and the line count. Files are not uploaded to Automata — workers access them directly via the configured path. Wordlists can be tagged with associated vulnerability types.

### Jobs

Jobs are the execution units. All current jobs are **HTTP jobs** (`HttpJob`), split into two concrete types backed by a single-table inheritance strategy:

- **`NarrowHttpJob`** — scoped to a single host. Targets one of: a single request, multiple requests under that host, a single equality set, or the host itself. Carries a `Duration` (`SHORT` or `LONG`) which determines which rate limit pool it runs in. Also holds an optional `Tenant` for authenticated testing.
- **`WideHttpJob`** — scoped to an entire program. Targets multiple hosts within that program.

A `GLOBAL` scope job spans hosts across multiple programs (STILL IN DEVELOPMENT).

Every job has a `HttpJobGenericDetails` embedded object containing:

| Field | Description |
|---|---|
| `httpJobScope` | `NARROW`, `WIDE`, or `GLOBAL` |
| `currentState` | Current position in the state machine |
| `requestedState` | State transition requested by a worker (e.g. `PAUSED`) |
| `verbosity` | `ResultsVerbosity` level for output detail |
| `priority` | 1–3 lazy, 4 eager, 5 reserved for retried eager runs |
| `rate` | Requests-per-unit rate for this job |
| `targetSelector` | Tagged-union: `selectorType` + polymorphic `selector` JSON |
| `genericConfig` | Match-and-replace rules |
| `customConfig` | Routine-specific JSON config, consumed only by the executor |

---

## Job Lifecycle

### State Machine

```
            ┌──────────┐
            │  DRAFT   │  ← created by user, not yet submitted
            └────┬─────┘
                 │ user action (queue / schedule)
        ┌────────┴────────┐
        ▼                 ▼
   ┌─────────┐      ┌───────────┐
   │ TOQUEUE │      │ SCHEDULED │  ← time-based, waits for trigger
   └────┬────┘      └─────┬─────┘
        │                 │ Spring scheduled task
        │           ┌─────▼──────┐
        │           │  TOQUEUE   │
        │           └─────┬──────┘
        └─────────────────┤
                          │ sync task: rate-limit check → enqueue to RabbitMQ
                          ▼
                    ┌──────────┐
                    │  QUEUED  │
                    └────┬─────┘
                         │ worker picks up
                         ▼
                    ┌──────────┐
                    │ RUNNING  │◄──────────┐
                    └────┬─────┘           │
                    ┌────┴──────────┐      │ resume
                    ▼               ▼      │
               ┌─────────┐    ┌─────────┐─┘
               │  PAUSED │    │(terminal)│
               └─────────┘    └─────────┘
                               FINISHED / CANCELED / FAILED / TERMINATED
```

Key transitions:

- `DRAFT → TOQUEUE` or `DRAFT → SCHEDULED` — user-initiated
- `SCHEDULED → TOQUEUE` — triggered by a Spring `@Scheduled` task when the scheduled time is reached
- `TOQUEUE → QUEUED` — handled by `HttpJobsSynchronizationService`, which runs intervally, checks rate limits, and publishes eligible jobs to RabbitMQ
- `QUEUED → RUNNING` — controlled by workers on successful start of a job
- `RUNNING ↔ PAUSED` — controlled by workers via the external integration API
- Terminal states (`FINISHED`, `CANCELED`, `FAILED`, `TERMINATED`) — set by workers upon completion

### Scoping Rules

Job scope is derived from what you select as the target, not chosen explicitly:

| Target selection | Scope |
|---|---|
| Single request | `NARROW` |
| Multiple requests, same host | `NARROW` |
| Single equality set | `NARROW` |
| Multiple equality sets, same host | `NARROW` |
| Single host | `NARROW` |
| Multiple hosts, same program | `WIDE` |
| Multiple hosts across programs | `GLOBAL` |

### Rate Limit Governance

`HttpJobsSynchronizationService` runs under `SERIALIZABLE` transaction isolation and enforces three layers of rate limits before promoting a job from `TOQUEUE` to `QUEUED`:

1. **Program rate limit** — the sum of rates of all running/queued jobs under the program must not exceed `program.programRateLimit`
2. **Host rate limit** — for `WIDE` jobs, each individual host in the target set is checked
3. **Duration pool limit** — for `NARROW` jobs, the job's rate is checked against either `host.shortRateLimit` or `host.longRateLimit` depending on its `Duration`

Priority of jobs is also taken into consideration.

---

## Request Ingestion

Requests can enter Automata through two paths:

**1. Burp Suite Extension**

A custom Burp extension sends intercepted requests (and optionally responses) to the external ingestion endpoint:

**2. Manual Entry**

Requests can be added manually through the UI

## Orchestrator (Worker)

The Go-based Orchestrator is a standalone worker service that:

- Connects to RabbitMQ and consumes jobs from the HTTP job queue
- Uses a unified SDK with per-tool connectors to execute CLI-based pentesting tools
- Reads the job's `customConfig` JSON to configure tool parameters per-routine
- Reports state transitions back to the core service via `POST /api/external/jobs/http/{jobId}/state`
- Writes results to RustFS (S3-compatible storage) and notifies the core service on completion
- Respects the rate limits enforced by the core service — multiple orchestrator instances can run concurrently; they do not coordinate directly but stay within the limits already established at queue time

Horizontal scaling is achieved by spawning additional orchestrator processes. Each instance independently consumes from the shared queue.

---

## Infrastructure

| Component | Technology | Purpose |
|---|---|---|
| Core service | Spring Boot 3.5 | REST API, job engine, scheduling, domain logic |
| Database | PostgreSQL 16 | All persistent state |
| Message queue | RabbitMQ 3.13 | Job delivery to orchestrators |
| Object storage | RustFS (S3-compatible) | Job result files, job config file snapshots |
| Worker | Go (Orchestrator) | Tool execution, distributed job consumption |

The core service uses:
- **Spring Data JPA + Hibernate** for persistence with JSONB columns for polymorphic embedded structures (`targetSelector`, `customConfig`, `genericConfig`, `authData`)
- **Spring AMQP** for RabbitMQ integration
- **Spring Cloud AWS S3** (`io.awspring.cloud`) for RustFS access
- **Spring Scheduler** (`@Scheduled`) for the 60-second synchronization task

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Java 21+ and Maven (for running outside Docker)
- A built Orchestrator binary (Go) configured to point at your RabbitMQ instance

### Running with Docker Compose

> NOTE: The frontend should be placed at the path specified in the docker compose

```bash
cd automata
docker compose up --build -d
```

The API will be available at `http://localhost:8080`.

> **RabbitMQ management UI:** `http://localhost:15672` (default credentials in `docker-compose.yml`)
>
> **RustFS console:** `http://localhost:9001`

### Stopping the app

```bash
cd automata
docker compose down
```