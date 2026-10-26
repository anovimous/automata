from typing import Any

from automata_mcp.core.models.base import CoreModel

MAX_PAGE_SIZE = 100
DEFAULT_PAGE_SIZE = 20


class Page[T](CoreModel):
    """A page of the core: ``{"content": [...], "page": 0, "size": 20, ...}``."""

    content: list[T]
    page: int = 0
    size: int = 0
    total_elements: int = 0
    total_pages: int = 0
    last: bool = True


def page_params(
    page: int = 0, size: int = DEFAULT_PAGE_SIZE, *, max_size: int = MAX_PAGE_SIZE
) -> dict[str, int]:
    """The ``page`` and ``size`` query parameters, clamped: page >= 0, 1 <= size <= max_size."""
    return {"page": max(page, 0), "size": min(max(size, 1), max_size)}


def drop_none(params: dict[str, Any]) -> dict[str, Any]:
    """Query parameters without the ones that were not given."""
    return {key: value for key, value in params.items() if value is not None}
