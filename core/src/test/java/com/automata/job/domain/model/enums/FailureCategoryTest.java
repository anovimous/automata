package com.automata.job.domain.model.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FailureCategoryTest {

	@Test
	void everyCategoryIsReadByItsName() {
		for (FailureCategory category : FailureCategory.values())
			assertEquals(category, FailureCategory.fromReported(category.name()));
	}

	@Test
	void aMissingNameIsUnknown() {
		assertEquals(FailureCategory.UNKNOWN, FailureCategory.fromReported(null));
	}

	@Test
	void aNameTheCoreDoesNotKnowIsUnknown() {
		assertEquals(FailureCategory.UNKNOWN, FailureCategory.fromReported("TARGET_UNREACHABLE"));
		assertEquals(FailureCategory.UNKNOWN, FailureCategory.fromReported(""));
		assertEquals(FailureCategory.UNKNOWN, FailureCategory.fromReported("tool_exit"));
	}

	@Test
	void surroundingSpacesAreIgnored() {
		assertEquals(FailureCategory.TOOL_EXIT, FailureCategory.fromReported(" TOOL_EXIT "));
	}

	@Test
	void theCategoriesAreTheSevenOfTheContract() {
		assertEquals(java.util.List.of("PREPARATION", "POLL", "INVALID_CONFIG", "TOOL_START", "TOOL_EXIT", "RESULT",
				"UNKNOWN"), java.util.Arrays.stream(FailureCategory.values()).map(Enum::name).toList());
	}

}
