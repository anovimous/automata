from automata_mcp.core.models.base import CoreModel
from automata_mcp.core.models.requests import BodyMetadata, HeaderEntry


class ResponseMetadata(CoreModel):
    response_id: int
    request_id: int
    host_id: int
    status_code: int | None = None
    insertion_date: str | None = None
    headers: list[HeaderEntry] = []
    body: BodyMetadata


class ResponseRaw(CoreModel):
    """``raw_body_base64`` is None when there is no body or the body is a file."""

    raw_head_base64: str
    raw_body_base64: str | None = None
    body_stored_as_file: bool = False
