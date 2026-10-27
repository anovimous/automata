.PHONY: sync lint format format-check typecheck test check

sync:
	uv sync

lint:
	uv run ruff check src tests scripts

format:
	uv run ruff format src tests scripts

format-check:
	uv run ruff format --check src tests scripts

typecheck:
	uv run mypy --strict src scripts

test:
	uv run pytest

# The quality gates every branch must pass.
check: lint format-check typecheck test
