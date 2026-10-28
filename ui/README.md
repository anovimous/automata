# Automata frontend

A React + TypeScript single-page application for the **Automata** security-testing
backend.

## Running locally

This app is ran using the docker compose file found at the central service at `github.com/anovimous/automata`

## Concepts

### Programs → Hosts → Tenants & Requests

The data model maps directly to the backend:

- **Programs** are the top-level scope (BugCrowd, HackerOne, Intigriti, or
  custom). Each program has hosts and a list of out-of-scope vulnerabilities.
- **Hosts** belong to one program. They can be FQDNs or wildcards, carry
  rate limits, and a description, technologies and notes (shown to AI agents).
- **Tenants** are auth-bearing identities scoped to a host. Each tenant has an
  optional static auth set (`headers`, `cookies`, `queryParameters`).
- **Requests** belong to a host. They store the parsed request line and headers
  plus the raw bytes; bodies are read through the metadata / raw / body-stream
  routes whether stored inline or as a file. The UI shows each request with its
  response (headers, raw message, body preview and download).
- **Equalizations** apply a comparator to the requests of a host and group the
  equivalent ones into **equality sets**.
- **Jobs** run a routine on a host or request. A user creates a draft, the job
  files are prepared, and the job is queued. Jobs created by an AI agent need a
  human approval first.
- **Findings** are vulnerabilities reported by a user or by an AI agent, with a
  human true/false-positive review.
- **Agent contexts** scope an AI agent to one host: the requests / equality sets
  it may work on, the routines it may run and the tenants it may use.

The routes that only the AI agent calls (`/api/agent-contexts/uuid/...`,
`/api/external/ai/...`, `POST /api/jobs/http/{id}/ai-result`) and the ones for
Burp and the workers (`/api/external/...`) have no UI on purpose.

`docs/rest-api-review.md` maps every REST route to its screen and lists the
backend problems found while wiring them; `src/config/features.ts` turns off the
controls whose backend support is missing.

### Custom configs and `public/routine-specs.json`

Each routine accepts a JSON object as its `customConfig`. The shape of that
object is routine-specific. Rather than push a free-form JSON editor on
users, the frontend reads a sidecar file at `public/routine-specs.json` that
maps routine keys to typed form schemas. See `src/lib/routineSpec.ts` for
the schema and `public/routine-specs.json` for two example entries.

Each field can declare:

- `type`: `string | integer | number | boolean | enum | enum-multi | string-list | object | array-of-objects`
- `required`, `default`, `locked`, `min`, `max`, `pattern`, `help`, `placeholder`
- `visibleWhen`: simple equality condition against a sibling field
- `options`: for enum/enum-multi
- `fields` (object) / `itemFields` (array-of-objects): for nested structures

Routines without a matching entry fall back to a Monaco JSON editor.

To add a routine spec:

1. Open `public/routine-specs.json` and add an entry under `routines`.
2. The key must match the routine's `key` field exactly (e.g.
   `"sql-injection-time-based"`).
3. In dev the file reloads on save. In Docker, either rebuild the image or
   bind-mount the file (see above).

## Keyboard shortcuts

- `⌘K` / `Ctrl K` — open command palette
- Within any data table:
  - `j` / `↓` — move focus down
  - `k` / `↑` — move focus up
  - `x` — toggle selection on focused row
  - `Enter` — open focused row

## Things not in v1

See `docs/rest-api-review.md` (section "Not possible with the current API") for the
full list. The main ones:

- Job scheduling, WIDE (multi-host) jobs and jobs on several requests: the backend
  answers 501 for them.
- Browsing the equality sets of a host: the API has no route that lists them, so a
  set is opened by id.
- Deleting programs, hosts, requests, routines, jobs and responses: not implemented
  by the backend.
