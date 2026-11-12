"""The enums of the core (plan section 6.3).

Every enum accepts a value it does not know: the member keeps the raw value, so it is shown and sent
back unchanged, and its ``name`` is ``UNKNOWN``. The core can add a value without breaking us.
"""

from enum import StrEnum
from typing import Self


class CoreEnum(StrEnum):
    @classmethod
    def _missing_(cls, value: object) -> Self | None:
        if not isinstance(value, str):
            return None
        member = str.__new__(cls, value)
        member._name_ = "UNKNOWN"
        member._value_ = value
        return member


class JobState(CoreEnum):
    DRAFT = "DRAFT"
    TOQUEUE = "TOQUEUE"
    SCHEDULED = "SCHEDULED"
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    PAUSED = "PAUSED"
    CANCELED = "CANCELED"
    FINISHED = "FINISHED"
    FAILED = "FAILED"
    SELFTERMINATED = "SELFTERMINATED"
    LOSTCONTACT = "LOSTCONTACT"


class FailureCategory(CoreEnum):
    PREPARATION = "PREPARATION"
    POLL = "POLL"
    INVALID_CONFIG = "INVALID_CONFIG"
    TOOL_START = "TOOL_START"
    TOOL_EXIT = "TOOL_EXIT"
    RESULT = "RESULT"
    UNKNOWN = "UNKNOWN"


class Origin(CoreEnum):
    """JobOrigin and FindingOrigin (same values)."""

    THIS_CONTEXT = "THIS_CONTEXT"
    OTHER_CONTEXT = "OTHER_CONTEXT"
    USER = "USER"


class ConfigFileStatus(CoreEnum):
    PREPARING = "PREPARING"
    READY = "READY"
    FAILED = "FAILED"


class ResultsVerbosity(CoreEnum):
    SUMMARIZED = "SUMMARIZED"
    NORMAL = "NORMAL"
    VERBOSE = "VERBOSE"


class Duration(CoreEnum):
    SHORT = "SHORT"
    LONG = "LONG"


class HttpJobScope(CoreEnum):
    NARROW = "NARROW"
    WIDE = "WIDE"


class JobCreator(CoreEnum):
    USER = "USER"
    AI = "AI"


class RepresentativeStrategy(CoreEnum):
    LATEST = "LATEST"
    AI_JUDGEMENT = "AI_JUDGEMENT"


class AgentContextItemType(CoreEnum):
    REQUEST = "REQUEST"
    EQUALITY_SET = "EQUALITY_SET"


class RequestContentType(CoreEnum):
    JSON = "JSON"
    FORM = "FORM"
    MULTIPART = "MULTIPART"
    XML = "XML"
    HTML = "HTML"
    JS = "JS"
    CSS = "CSS"
    TEXT = "TEXT"
    IMAGE = "IMAGE"
    AUDIO = "AUDIO"
    VIDEO = "VIDEO"
    PDF = "PDF"
    ARCHIVE = "ARCHIVE"
    OCTET_STREAM = "OCTET_STREAM"
    OTHER = "OTHER"


class Method(CoreEnum):
    GET = "GET"
    POST = "POST"
    PUT = "PUT"
    PATCH = "PATCH"
    DELETE = "DELETE"
    HEAD = "HEAD"


class Source(CoreEnum):
    MANUAL = "MANUAL"
    BURP = "BURP"
    WAYBACK = "WAYBACK"
    AI = "AI"


class BodyStorage(CoreEnum):
    NONE = "NONE"
    INLINE = "INLINE"
    FILE = "FILE"


class FindingStatus(CoreEnum):
    SUGGESTED = "SUGGESTED"
    CONFIRMED = "CONFIRMED"


class FindingCreator(CoreEnum):
    AI = "AI"
    USER = "USER"


class HumanVerdict(CoreEnum):
    TRUE_POSITIVE = "TRUE_POSITIVE"
    FALSE_POSITIVE = "FALSE_POSITIVE"


class ResultContentType(CoreEnum):
    JSON = "JSON"
    TEXT = "TEXT"


class TargetCardinalityPair(CoreEnum):
    SINGLE_HOST = "SINGLE_HOST"
    MULTILPLE_HOSTS = "MULTILPLE_HOSTS"  # sic: the typo is the core's
    SINGLE_REQUEST = "SINGLE_REQUEST"
    MULTIPLE_REQUESTS = "MULTIPLE_REQUESTS"


class HostScope(CoreEnum):
    WILDCARD = "WILDCARD"
    FQDN = "FQDN"


class SelectorType(CoreEnum):
    SINGLE_HOST = "SINGLE_HOST"
    SINGLE_REQUEST = "SINGLE_REQUEST"
    MULTIPLE_HOSTS = "MULTIPLE_HOSTS"
    MULTIPLE_REQUESTS = "MULTIPLE_REQUESTS"
