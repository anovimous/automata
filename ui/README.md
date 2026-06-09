# Automata frontend

A React + TypeScript single-page application for the **Automata** security-testing
backend.

## Running locally

This app is ran using the docker compose file found at the central service at `github.com/anovimous/automata`

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

- Job scheduling UI (the backend endpoints are commented out)
- Per-member listing of an equality set (backend does not expose this endpoint)
