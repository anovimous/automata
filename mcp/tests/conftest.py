import json
import os
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


@pytest.fixture(autouse=True)
def clean_automata_env(monkeypatch: pytest.MonkeyPatch) -> None:
    """No test sees an AUTOMATA_* variable of the machine it runs on."""
    for name in [name for name in os.environ if name.startswith("AUTOMATA_")]:
        monkeypatch.delenv(name)
