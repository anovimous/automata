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
