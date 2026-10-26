import pytest
from pydantic import BaseModel

from automata_mcp.core.models.enums import (
    BodyStorage,
    FailureCategory,
    JobState,
    Origin,
    TargetCardinalityPair,
)


def test_known_values_give_the_named_member() -> None:
    assert JobState("FINISHED") is JobState.FINISHED
    assert Origin("OTHER_CONTEXT") is Origin.OTHER_CONTEXT


def test_members_compare_equal_to_their_string() -> None:
    assert JobState.RUNNING == "RUNNING"
    assert str(JobState.RUNNING) == "RUNNING"


def test_unknown_value_is_kept_and_named_unknown() -> None:
    state = JobState("SOMETHING_NEW")

    assert state.name == "UNKNOWN"
    assert state.value == "SOMETHING_NEW"
    assert state == "SOMETHING_NEW"
    assert str(state) == "SOMETHING_NEW"
    assert state not in set(JobState.__members__.values())


def test_unknown_value_is_accepted_inside_a_model_and_dumped_unchanged() -> None:
    class Holder(BaseModel):
        storage: BodyStorage

    holder = Holder.model_validate({"storage": "S3"})

    assert holder.storage.name == "UNKNOWN"
    assert holder.model_dump(mode="json") == {"storage": "S3"}


def test_a_value_that_is_not_a_string_is_still_rejected() -> None:
    with pytest.raises(ValueError, match="is not a valid"):
        JobState(5)  # type: ignore[arg-type]


def test_failure_category_has_its_own_unknown_member() -> None:
    assert FailureCategory("UNKNOWN") is FailureCategory.UNKNOWN


def test_the_typo_of_the_core_is_kept() -> None:
    assert TargetCardinalityPair("MULTILPLE_HOSTS") is TargetCardinalityPair.MULTILPLE_HOSTS
