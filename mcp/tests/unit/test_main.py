import runpy
import sys
from typing import Any

import pytest

from automata_mcp import __main__ as entry
from automata_mcp.config import Settings


def test_main_loads_the_settings_configures_logging_and_runs_the_server(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("AUTOMATA_CORE_URL", "http://core:8080")
    monkeypatch.setenv("AUTOMATA_LOG_LEVEL", "DEBUG")
    calls: list[tuple[str, Any]] = []
    sentinel = object()

    monkeypatch.setattr(entry, "configure_logging", lambda level: calls.append(("logging", level)))

    def fake_create_server(settings: Settings) -> object:
        calls.append(("create", settings.core_url))
        return sentinel

    def fake_run_server(server: object, settings: Settings) -> None:
        calls.append(("run", server is sentinel and settings.transport))

    monkeypatch.setattr(entry, "create_server", fake_create_server)
    monkeypatch.setattr(entry, "run_server", fake_run_server)

    entry.main()

    assert calls == [("logging", "DEBUG"), ("create", "http://core:8080"), ("run", "stdio")]


def test_the_module_can_be_run_with_python_dash_m(monkeypatch: pytest.MonkeyPatch) -> None:
    started: list[bool] = []
    monkeypatch.setattr(
        "automata_mcp.server.run_server", lambda server, settings: started.append(True)
    )
    monkeypatch.setattr("automata_mcp.logging_setup.configure_logging", lambda level: None)
    monkeypatch.setattr("automata_mcp.server.create_server", lambda settings: object())

    monkeypatch.delitem(sys.modules, "automata_mcp.__main__")
    runpy.run_module("automata_mcp", run_name="__main__")

    assert started == [True]


def test_the_console_script_points_to_main() -> None:
    from importlib.metadata import entry_points

    scripts = {ep.name: ep.value for ep in entry_points(group="console_scripts")}

    assert scripts["automata-mcp"] == "automata_mcp.__main__:main"
