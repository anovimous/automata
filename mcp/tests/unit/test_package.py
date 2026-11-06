from importlib.metadata import version

import automata_mcp


def test_package_is_installed_with_a_version() -> None:
    assert version("automata-mcp")


def test_package_has_a_docstring() -> None:
    assert automata_mcp.__doc__
