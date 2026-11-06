from automata_mcp.config import Settings
from automata_mcp.logging_setup import configure_logging
from automata_mcp.server import create_server, run_server


def main() -> None:
    """Entry point of the ``automata-mcp`` command: load the settings and run the server."""
    settings = Settings()
    configure_logging(settings.log_level)
    run_server(create_server(settings), settings)


if __name__ == "__main__":
    main()
