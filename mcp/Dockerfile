# automata-mcp over streamable HTTP. Build: docker build -t automata-mcp .
# Run:   docker run --rm -p 8000:8000 -e AUTOMATA_CORE_URL=http://core:8080 automata-mcp
#
# The server has no authentication, like the core: do not publish its port beyond a trusted network.

# ---------------------------------------------------------------- build
FROM python:3.12-slim AS builder

COPY --from=ghcr.io/astral-sh/uv:0.13.5 /uv /usr/local/bin/uv

ENV UV_LINK_MODE=copy \
    UV_COMPILE_BYTECODE=1 \
    UV_PYTHON_DOWNLOADS=0

WORKDIR /app

# The dependencies first, so that a change in the source does not install them again.
COPY pyproject.toml uv.lock README.md ./
RUN uv sync --frozen --no-dev --no-install-project

# Not editable: the runtime stage copies only the environment, not the source.
COPY src ./src
RUN uv sync --frozen --no-dev --no-editable

# ---------------------------------------------------------------- run
FROM python:3.12-slim

RUN groupadd --system --gid 10001 automata \
    && useradd --system --uid 10001 --gid automata --no-create-home --shell /usr/sbin/nologin automata

WORKDIR /app
COPY --from=builder --chown=automata:automata /app/.venv /app/.venv

ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    AUTOMATA_TRANSPORT=streamable-http \
    AUTOMATA_HTTP_HOST=0.0.0.0 \
    AUTOMATA_HTTP_PORT=8000

USER automata
EXPOSE 8000

# The server is up when the port accepts a connection (it also starts when the core is down).
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import os, socket; socket.create_connection(('127.0.0.1', int(os.environ['AUTOMATA_HTTP_PORT'])), 3)"

ENTRYPOINT ["automata-mcp"]
