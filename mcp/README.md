# automata-mcp

MCP server of the Automata automation framework (security testing of HTTP APIs). An AI agent talks to
this server over MCP; the server turns the agent's tool calls into REST calls on the Automata core and
returns the answers in a form the agent can reason about. It persists nothing and holds no business logic.

Status: implemented (37 tools, a workflow prompt, two resources, a Docker image and a compose service).
The plan, and what each phase did, is in [`docs/implementation-plan.md`](docs/implementation-plan.md).

## Development

Requires Python 3.12+ and [uv](https://docs.astral.sh/uv/).

```
make sync     # install dependencies
make check    # ruff check, ruff format --check, mypy --strict, pytest
```

Configuration is documented in [`.env.example`](.env.example). The routes and payloads of the core that the
server uses are in [`docs/core-contract.md`](docs/core-contract.md).

## Run

```
uv run automata-mcp                                   # stdio, for a local MCP client
AUTOMATA_TRANSPORT=streamable-http uv run automata-mcp  # HTTP on 0.0.0.0:8000, path /mcp
```

Set `AUTOMATA_CORE_URL` to the core (default `http://localhost:8080`). The server starts even when the
core is down and says so in its log (stderr); its tools report the problem when they are used.
Example client entry for stdio: command `uv`, args `["--directory", "/path/to/automata-mcp", "run",
"automata-mcp"]`, env `AUTOMATA_CORE_URL`.

## Docker

The image runs the server over streamable HTTP (`/mcp` on port 8000), as a non-root user:

```
docker build -t automata-mcp .
docker run --rm -p 8000:8000 -e AUTOMATA_CORE_URL=http://core:8080 automata-mcp
```

Configure it with the `AUTOMATA_*` variables of [`.env.example`](.env.example) (at least
`AUTOMATA_CORE_URL`: inside a container `localhost` is the container itself). The image defaults to
`AUTOMATA_TRANSPORT=streamable-http`, `AUTOMATA_HTTP_HOST=0.0.0.0` and `AUTOMATA_HTTP_PORT=8000`. Its
health check succeeds when the port accepts a connection; the server also starts when the core is
down. Like the core, the server has no authentication: do not publish its port beyond a trusted
network. For stdio use `uv run automata-mcp` instead (see Run).

### With the core (docker compose)

The core's `docker-compose.yml` has an `automata-mcp` service that builds this image from
`../../../automata-mcp`. `docker compose up --build` in the core repo starts it with the rest:

- from the host: `http://localhost:8000/mcp` (the port is published on `127.0.0.1` only, since there is
  no authentication);
- from another container of the stack: `http://automata-mcp:8000/mcp`;
- it reaches the core at `http://app:8080`. It may start a few seconds before the app and log that it
  cannot reach it; it keeps running and works as soon as the app is up.

## Integration tests

The unit tests use a fake core. `tests/integration` runs the real server against a real core and is
skipped unless asked for:

```
# in the core repo: docker compose up -d --build    (needs a database volume without old tables)
AUTOMATA_IT=1 AUTOMATA_CORE_URL=http://localhost:8080 uv run pytest tests/integration
```

Each run seeds its own uniquely named data through the core's REST API, so it can be repeated.

## Checking the routes against the core

`scripts/check_core_contract.py` calls every method of the client against a recording transport and
checks that each route, HTTP method and query parameter exists in the core's OpenAPI document:

```
uv run python scripts/check_core_contract.py --core-url http://localhost:8080   # or --file openapi.json
```

Exit status 0: all exist; 1: something is missing; 2: the document could not be read.
