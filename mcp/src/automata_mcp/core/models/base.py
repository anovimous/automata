from typing import Any

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CoreModel(BaseModel):
    """Base of every model: camelCase aliases, unknown fields ignored (the core evolves)."""

    model_config = ConfigDict(extra="ignore", populate_by_name=True, alias_generator=to_camel)


class CoreRequest(CoreModel):
    """Base of the bodies sent to the core."""

    def to_payload(self) -> dict[str, Any]:
        """The JSON body: camelCase keys, fields that were not given are left out (never null)."""
        return self.model_dump(mode="json", by_alias=True, exclude_none=True)
