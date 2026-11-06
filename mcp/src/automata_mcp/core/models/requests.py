from automata_mcp.core.models.base import CoreModel, CoreRequest
from automata_mcp.core.models.enums import BodyStorage, Method, RequestContentType, Source


class HeaderEntry(CoreModel):
    name: str
    value: str


class BodyMetadata(CoreModel):
    storage: BodyStorage
    # The Content-Type header as received (None when there was none).
    content_type: str | None = None
    # The name of the mime family the core classified the body into (JSON, FORM, ...).
    mime_family: str | None = None
    size_bytes: int = 0


class RequestLine(CoreModel):
    method: Method
    # The path with its variable segments abstracted.
    computated_path: str
    extension: str | None = None
    version: str
    # The request target exactly as sent (path and query string).
    target: str


class RequestMetadata(CoreModel):
    request_id: int
    host_id: int
    program_id: int | None = None
    tenant_id: int | None = None
    response_id: int | None = None
    source: Source
    insertion_date: str | None = None
    line: RequestLine
    headers: list[HeaderEntry] = []
    body: BodyMetadata


class RequestRaw(CoreModel):
    """``raw_body_base64`` is None when there is no body or the body is a file."""

    raw_head_base64: str
    raw_body_base64: str | None = None
    body_stored_as_file: bool = False
    response_id: int | None = None


class RequestSummary(CoreModel):
    """The answer of ``add_request``."""

    request_id: int
    method: Method
    computated_path: str
    extension: str | None = None
    version: str
    number_of_properties: int = 0
    content_type: RequestContentType | None = None
    source: Source
    host_id: int
    tenant_id: int | None = None
    program_id: int | None = None
    response_id: int | None = None
    body_stored_as_file: bool = False
    body_size_bytes: int = 0


class AiRequestAddition(CoreRequest):
    """Body of ``POST /api/external/ai/requests``; ``host`` is the host name."""

    request_base64: str
    host: str
    base64_response: str | None = None
    tenant_id: int | None = None
    agent_context_uuid: str | None = None
