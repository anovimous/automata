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

}
