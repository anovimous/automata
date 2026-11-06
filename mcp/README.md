# MCP server

MCP server of the Automata automation framework (security testing of HTTP APIs). An AI agent talks to
this server over MCP; the server turns the agent's tool calls into REST calls on the Automata core and
returns the answers in a form the agent can reason about. It persists nothing and holds no business logic.

Status: implemented (37 tools, a workflow prompt, two resources, a Docker image and a compose service).
The plan, and what each phase did, is in [`docs/implementation-plan.md`](docs/implementation-plan.md).

## Run

The `docker-compose.yml` at the root of this repository has an `automata-mcp` service that builds this
folder's `Dockerfile`. `docker compose up --build` at the repository root starts it with the rest of the
stack. The image runs the server over streamable HTTP (`/mcp` on port 8000) as a non-root user:

- from the host: `http://localhost:8000/mcp` (the port is published on `127.0.0.1` only, since there is
  no authentication); point the MCP client of the agent at this URL;
- from another container of the stack: `http://automata-mcp:8000/mcp`;
- it reaches the core at `http://app:8080` (`AUTOMATA_CORE_URL` in the compose file). It may start a few
  seconds before the app and log that it cannot reach it; it keeps running and works as soon as the app
  is up.

Like the core, the server has no authentication: do not publish its port beyond a trusted network. The
other `AUTOMATA_*` settings are documented in [`.env.example`](.env.example).

## Documents

- [`docs/core-contract.md`](docs/core-contract.md): the core routes and payloads the server uses.
- [`docs/implementation-plan.md`](docs/implementation-plan.md): the plan and the tool catalog.
