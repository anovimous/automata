.PHONY: sync lint format format-check typecheck test check

sync:
	uv sync

lint:
	uv run ruff check src tests

format:
	uv run ruff format src tests

format-check:
	uv run ruff format --check src tests

typecheck:
	uv run mypy --strict src

test:
	uv run pytest

# The quality gates every branch must pass.
check: lint format-check typecheck test
