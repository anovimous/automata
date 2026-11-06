from typing import Any

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CoreModel(BaseModel):
    """Base of every model: camelCase aliases, unknown fields ignored (the core evolves)."""

    model_config = ConfigDict(extra="ignore", populate_by_name=True, alias_generator=to_camel)

    def to_output(self) -> dict[str, Any]:
        """The model as JSON-ready data for the agent: camelCase keys as the core names them,
        fields that are null left out (null inside free-form JSON, like a schema, is kept)."""
        return self.model_dump(mode="json", by_alias=True, exclude_none=True)


class CoreRequest(CoreModel):
    """Base of the bodies sent to the core."""

    def to_payload(self) -> dict[str, Any]:
        """The JSON body: camelCase keys, fields that were not given are left out (never null)."""
        return self.to_output()
