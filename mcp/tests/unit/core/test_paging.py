from collections.abc import Callable
from typing import Any

import pytest

from automata_mcp.core.models.reference import VulnerabilitySummary
from automata_mcp.core.paging import MAX_PAGE_SIZE, Page, drop_none, page_params


def test_page_parses_the_core_wrapper(load_fixture: Callable[[str], Any]) -> None:
    page = Page[VulnerabilitySummary].model_validate(load_fixture("vulnerability_summary_page"))

    assert [item.name for item in page.content] == ["BOLA", "SQL injection"]
    assert (page.page, page.size, page.total_elements, page.total_pages, page.last) == (
        0,
        30,
        2,
        1,
        True,
    )


def test_page_serializes_with_camel_case_aliases() -> None:
    page = Page[int](content=[1], total_elements=1, total_pages=1)

    assert page.model_dump(by_alias=True)["totalElements"] == 1


@pytest.mark.parametrize(
    ("page", "size", "expected"),
    [
        (0, 20, {"page": 0, "size": 20}),
        (3, 50, {"page": 3, "size": 50}),
        (-4, 20, {"page": 0, "size": 20}),
        (0, 0, {"page": 0, "size": 1}),
        (0, -5, {"page": 0, "size": 1}),
        (0, 5000, {"page": 0, "size": MAX_PAGE_SIZE}),
    ],
)
def test_page_params_are_clamped(page: int, size: int, expected: dict[str, int]) -> None:
    assert page_params(page, size) == expected


def test_page_params_use_the_default_size() -> None:
    assert page_params() == {"page": 0, "size": 20}


def test_page_params_accept_another_maximum() -> None:
    assert page_params(0, 500, max_size=200)["size"] == 200


def test_drop_none_keeps_falsy_values_that_were_given() -> None:
    assert drop_none({"a": None, "b": 0, "c": "", "d": False, "e": 5}) == {
        "b": 0,
        "c": "",
        "d": False,
        "e": 5,
    }
