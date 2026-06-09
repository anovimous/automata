# Automata frontend

A React + TypeScript single-page application for the **Automata** security-testing
backend (Spring Boot 3, port 8080). Keyboard-first, dense, Linear/Vercel-style UI
designed for power users who run many concurrent vulnerability-hunting jobs.

## Stack

- **Vite** + **React 18** + **TypeScript** (strict)
- **TanStack Router** (file-tree-style code-defined routes, fully typed)
- **TanStack Query** for server state, polling, and cache invalidation
- **Tailwind CSS** with hand-authored shadcn-style primitives over Radix
- **cmdk** for the `⌘K` command palette
- **Monaco Editor** as the fallback JSON editor for routine custom configs
- **Zod** for runtime validation of API responses
- **lucide-react** for icons
- Fonts: **Geist** (sans) + **JetBrains Mono** via Google Fonts

No global UI framework or design system has been pulled in — every primitive
(`Button`, `Card`, `Tabs`, `Popover`, `Dialog`, etc.) is hand-authored in
`src/components/ui/` so the look stays cohesive and the bundle stays small.

## Running locally

```sh
npm install
npm run dev
```

The dev server starts on `http://localhost:5173` and proxies `/api/*` to
`http://localhost:8080` (the Spring Boot backend). To target a different
backend, set `VITE_DEV_API_TARGET`:

```sh
VITE_DEV_API_TARGET=http://10.0.0.5:8080 npm run dev
```

Build a production bundle with:

```sh
npm run build
npm run preview   # local preview of the production bundle
```

## Running in Docker (alongside the backend)

The repo ships a multi-stage `Dockerfile` that produces a small nginx image
serving the SPA on port 80 and reverse-proxying `/api` to the backend service.

To add the frontend to the backend's `docker-compose.yml`, paste in the
snippet at the project root:

```sh
cat docker-compose.snippet.yml >> path/to/backend/docker-compose.yml
```

The snippet adds a `frontend` service on the same `backend` Docker network as
the Spring Boot app. By default it expects the backend container to be named
`app` and reachable at `http://app:8080`. After paste-in:

```sh
docker compose up -d --build frontend
# UI:      http://localhost:3000
# Backend: http://localhost:8080 (unchanged)
```

If your backend service has a different container name, edit `nginx.conf`'s
`proxy_pass http://app:8080;` line to match.

### Live-reloading the routine specs file

The compose snippet includes (commented out) a bind-mount for
`./public/routine-specs.json` so you can iterate on routine forms without
rebuilding the image:

```yml
# In docker-compose.snippet.yml, uncomment the volumes block:
volumes:
  - ./public/routine-specs.json:/usr/share/nginx/html/routine-specs.json:ro
```

## Project layout

```
src/
├── api/                  Thin per-resource fetchers over `apiFetch<T>`
├── components/
│   ├── ui/               Hand-authored primitives (Button, Card, Tabs…)
│   ├── common/           Generic display blocks (EmptyState, ConfirmDialog…)
│   ├── data-table/       Reusable dense table with selection + keyboard nav
│   ├── job/              Job-domain pills (state, scope, method)
│   ├── run-job/          Run-job form pieces (routine picker, custom config…)
│   ├── cmdk/             ⌘K command palette
│   ├── layout/           Sidebar, top bar, page wrappers, theme toggle
│   ├── program/          Create-program dialog
│   ├── host/             Create/edit-host dialogs
│   ├── tenant/           Create-tenant dialog (auth editor lives in route)
│   ├── request/          Add-raw-request dialog
│   ├── routine/          Create-routine dialog
│   ├── comparator/       Create-comparator dialog
│   ├── modifier/         Create-modifier dialog
│   └── wordlist/         Create-wordlist dialog
├── config/
│   └── features.ts       Feature flags for backend-stubbed selectors
├── hooks/                One file per resource: TanStack Query hooks
├── lib/
│   ├── fetcher.ts        `apiFetch<T>` + ApiError + query-string helper
│   ├── queryClient.ts    Configured QueryClient
│   ├── inferScope.ts     Selection → scope/selector inference
│   ├── routineSpec.ts    Loader + validator + builder for routine-specs.json
│   ├── activity.ts       LocalStorage activity log for the dashboard
│   ├── utils.ts          Theme, base64, relative-time, downloadBlob
│   └── cn.ts             clsx + twMerge
├── routes/
│   ├── router.tsx        Root route tree (TanStack Router)
│   ├── dashboard.tsx     Landing page (4 panels)
│   ├── programs/         programs/list + programs/detail (= hosts in program)
│   ├── hosts/            All host sub-pages incl. run-job
│   ├── jobs/             jobs/list + jobs/detail + jobs/run-global
│   ├── routines/         routines/list + routines/detail
│   ├── vulnerabilities/  vulnerabilities/list + vulnerabilities/detail
│   ├── comparators/      comparators/list + comparators/detail
│   ├── modifiers/        modifiers/list + modifiers/detail
│   ├── wordlists/        wordlists/list + wordlists/detail
│   └── settings.tsx
├── styles/
│   └── globals.css       Tailwind + CSS variables + custom utilities
├── types/
│   ├── enums.ts          Backend enums + state-machine helpers
│   ├── pagination.ts     Pageable + PageHolder<T> + Spring sort helpers
│   └── domain.ts         Zod schemas + interfaces for every entity
└── main.tsx              Entry point
```

## Concepts

### Programs → Hosts → Tenants & Requests

The data model maps directly to the backend:

- **Programs** are the top-level scope (BugCrowd, HackerOne, Intigriti, or
  custom). Each program has hosts.
- **Hosts** belong to one program. They can be FQDNs or wildcards and carry
  per-host rate limits.
- **Tenants** are auth-bearing identities scoped to a host. Each tenant has
  optional Static (`headers`, `cookies`, `queryParameters`) or Dynamic
  (JS/Groovy `code`) auth data.
- **Requests** belong to a host. They store parsed method/path/contentType
  plus the original raw bytes (added MANUAL or WAYBACK).
- **Equality sets** are groups of requests that a comparator considers
  equivalent. They are produced by running `equalize`.

The UI follows this nesting closely: there is no global list of tenants,
requests, or equality sets — they are reached via their parent host page.

### Jobs and scope inference

The backend distinguishes three job scopes:

- **NARROW** — one request, multiple requests under the same host, a single
  host, or one/more equality sets all on the same host.
- **WIDE** — multiple hosts in the same program.
- **GLOBAL** — hosts spanning multiple programs.

The UI never asks the user to pick a scope. The job-creation page is reached
from a context (a request, a multi-select on the requests page, a host's
"Run job" button, etc.) and `lib/inferScope.ts` deterministically maps the
selection to scope + selector + a backend-readiness flag. The only
exception is GLOBAL, which is started via the dedicated **New global job**
button on the `/jobs` page because GLOBAL selectors are usually empty.

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

### Feature flags

`src/config/features.ts` carries three flags that block submission of
selector types and operations the backend has not yet implemented:

| Flag                                  | What it gates                                                  |
| ------------------------------------- | -------------------------------------------------------------- |
| `WIDE_MULTI_HOSTS_BACKEND_READY`      | Selecting multiple hosts in a program for a WIDE job          |
| `MULTI_EQUALITY_SETS_BACKEND_READY`   | Selecting multiple equality sets under one host                |
| `JOB_SCHEDULING_BACKEND_READY`        | The job-scheduling UI (commented out backend-side as of v0.1) |

When a flag is off and the user lands on an affected flow, the form still
renders but a banner blocks submission. Flip the flag to `true` once the
backend ships the change.

## Keyboard shortcuts

- `⌘K` / `Ctrl K` — open command palette
- Within any data table:
  - `j` / `↓` — move focus down
  - `k` / `↑` — move focus up
  - `x` — toggle selection on focused row
  - `Enter` — open focused row

## Things not in v1

- Job scheduling UI (the backend endpoints are commented out)
- Wordlist file upload (backend exposes path + name only)
- Cross-program multi-host selection (GLOBAL is the dedicated escape hatch)
- Per-member listing of an equality set (backend does not expose this endpoint)

These are gated by the feature flags above where relevant; everything else
is purely a UI gap waiting on backend support.

## Production notes

- Bundled with Vite into static assets; served by nginx with SPA fallback.
- All API calls go through `/api/*` which is reverse-proxied by nginx —
  the frontend itself does **not** know the backend's URL or port.
- Long-running streaming responses (e.g. `GET /api/jobs/{id}/result`) are
  fine: nginx is configured with `proxy_buffering off` and a generous
  `proxy_read_timeout`.
- Dark mode is the default. The toggle lives in the top bar and in Settings.
