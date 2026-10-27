# automata-mcp

MCP server of the Automata automation framework (security testing of HTTP APIs). An AI agent talks to
this server over MCP; the server turns the agent's tool calls into REST calls on the Automata core and
returns the answers in a form the agent can reason about. It persists nothing and holds no business logic.

Status: under construction. The plan is in [`docs/implementation-plan.md`](docs/implementation-plan.md).

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
