import json
from collections.abc import Callable
from pathlib import Path
from typing import Any

import pytest

FIXTURES_DIR = Path(__file__).parent / "fixtures" / "core"


@pytest.fixture
def load_fixture() -> Callable[[str], Any]:
    """Load a sample payload of the core from ``tests/fixtures/core/<name>.json``."""

    def load(name: str) -> Any:
        return json.loads((FIXTURES_DIR / f"{name}.json").read_text(encoding="utf-8"))

    return load
