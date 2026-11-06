"""The ``custom_config`` of a job: one convenience translation, no validation.

The core validates the config against the routine's schema and answers 400 with the violations;
the server never repeats that. The only thing it does is let the agent write a raw request as text.
"""

from typing import Any

from automata_mcp.services.raw_messages import normalize_raw_message, to_base64


def expand_raw_request_text(custom_config: dict[str, Any] | None) -> dict[str, Any] | None:
    """The config with ``rawRequestText`` turned into the ``rawRequestBase64`` the core expects.

    A string ``rawRequestText`` is normalized (CRLF head, blank line, body untouched), encoded and
    stored as ``rawRequestBase64``, and the text key is removed so the core only ever sees the
    base64 one. If ``rawRequestBase64`` is already there it wins and the text is dropped. A
    ``rawRequestText`` that is not a string is left for the core to reject. The input is not
    modified.
    """
    if not custom_config or "rawRequestText" not in custom_config:
        return custom_config
    text = custom_config["rawRequestText"]
    if not isinstance(text, str):
        return custom_config
    expanded = {key: value for key, value in custom_config.items() if key != "rawRequestText"}
    if "rawRequestBase64" not in expanded:
        expanded["rawRequestBase64"] = to_base64(normalize_raw_message(text))
    return expanded
