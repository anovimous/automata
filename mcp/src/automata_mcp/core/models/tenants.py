from automata_mcp.core.models.base import CoreModel


class AuthNameValuePair(CoreModel):
    key: str
    value: str


class StaticData(CoreModel):
    headers: list[AuthNameValuePair] = []
    cookies: list[AuthNameValuePair] = []
    query_parameters: list[AuthNameValuePair] = []


class StaticAuthData(CoreModel):
    data: StaticData | None = None
    # An instant, despite the name (the core serializes an Instant): kept as the ISO string.
    life_time_in_seconds: str | None = None
    populated_at: str | None = None


class AgentContextTenant(CoreModel):
    """A tenant of the context, with its authentication (``None`` when it has none)."""

    tenant_id: int
    name: str | None = None
    email: str | None = None
    auth_data: StaticAuthData | None = None
    request_ids: list[int] = []
