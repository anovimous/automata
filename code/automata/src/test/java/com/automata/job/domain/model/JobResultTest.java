package com.automata.job.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JobResultTest {

	@Test
	void shortMessageIsKept() {
		assertEquals("out of memory", JobResult.failed("out of memory").getFailureMsg());
	}

	@Test
	void messageOfTheMaximumLengthIsKept() {
		String message = "a".repeat(JobResult.MAX_FAILURE_MSG_LENGTH);

		assertEquals(message, JobResult.failed(message).getFailureMsg());
	}

	@Test
	void longerMessageIsCutToTheMaximumLength() {
		String message = "a".repeat(JobResult.MAX_FAILURE_MSG_LENGTH) + "overflow";

		assertEquals("a".repeat(JobResult.MAX_FAILURE_MSG_LENGTH), JobResult.failed(message).getFailureMsg());
	}

	@Test
	void theMaximumLengthIs500() {
		assertEquals(500, JobResult.MAX_FAILURE_MSG_LENGTH);
	}

	@Test
	void aFailureWithoutACategoryIsUnknown() {
		assertEquals(com.automata.job.domain.model.enums.FailureCategory.UNKNOWN,
				JobResult.failed("boom").getFailureCategory());
		assertEquals(com.automata.job.domain.model.enums.FailureCategory.UNKNOWN,
				JobResult.failed("boom", null).getFailureCategory());
	}

	@Test
	void theCategoryIsKept() {
		JobResult result = JobResult.failed("boom", com.automata.job.domain.model.enums.FailureCategory.TOOL_EXIT);

		assertEquals(com.automata.job.domain.model.enums.FailureCategory.TOOL_EXIT, result.getFailureCategory());
		assertEquals("boom", result.getFailureMsg());
	}

	@Test
	void aFailureWithoutAMessageKeepsItsCategory() {
		JobResult result = JobResult.failed(null, com.automata.job.domain.model.enums.FailureCategory.POLL);

		org.junit.jupiter.api.Assertions.assertNull(result.getFailureMsg());
		assertEquals(com.automata.job.domain.model.enums.FailureCategory.POLL, result.getFailureCategory());
	}

}
