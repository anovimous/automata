package com.automata.job.domain.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.automata.job.domain.model.enums.JobState;

class InvalidJobStateExceptionTest {

	@Test
	void requiredStateNamesTheOperationAndBothStates() {
		InvalidJobStateException ex = InvalidJobStateException.requiredState(JobState.RUNNING, JobState.DRAFT,
				"pause it");

		assertEquals("The job must be in the RUNNING state in order to pause it, but it is DRAFT", ex.getMessage());
	}

}
