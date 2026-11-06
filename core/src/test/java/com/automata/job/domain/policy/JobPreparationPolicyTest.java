package com.automata.job.domain.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.model.enums.ConfigFileStatus;
import com.automata.job.domain.model.enums.JobState;

class JobPreparationPolicyTest {

	// ---- requireReadyToPrepareForQueueing

	@Test
	void jobWithReadyFilesCanBePreparedForQueueing() {
		assertDoesNotThrow(() -> JobPreparationPolicy.requireReadyToPrepareForQueueing(ConfigFileStatus.READY));
	}

	@ParameterizedTest
	@EnumSource(value = ConfigFileStatus.class, names = "READY", mode = EnumSource.Mode.EXCLUDE)
	void jobWithoutReadyFilesCannotBePreparedForQueueing(ConfigFileStatus status) {
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
				() -> JobPreparationPolicy.requireReadyToPrepareForQueueing(status));
		assertEquals("The files of the job must be READY in order to prepare it for queueing, but they are " + status,
				ex.getMessage());
	}

	// ---- requireRetryable

	@ParameterizedTest
	@EnumSource(value = ConfigFileStatus.class, names = { "PREPARING", "FAILED" })
	void draftJobWithPreparingOrFailedFilesIsRetryable(ConfigFileStatus status) {
		assertDoesNotThrow(() -> JobPreparationPolicy.requireRetryable(JobState.DRAFT, status));
	}

	@Test
	void draftJobWithReadyFilesIsNotRetryable() {
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
				() -> JobPreparationPolicy.requireRetryable(JobState.DRAFT, ConfigFileStatus.READY));
		assertEquals(
				"The files of the job must be PREPARING or FAILED in order to retry their preparation, but they are READY",
				ex.getMessage());
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
	void onlyDraftJobIsRetryable(JobState current) {
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
				() -> JobPreparationPolicy.requireRetryable(current, ConfigFileStatus.FAILED));
		assertEquals("The job must be in the DRAFT state in order to retry the preparation of its files, but it is "
				+ current, ex.getMessage());
	}

	// ---- limitFailure

	@Test
	void shortFailureIsKept() {
		assertEquals("boom", JobPreparationPolicy.limitFailure("boom"));
	}

	@Test
	void failureOfExactlyTheMaximumLengthIsKept() {
		String message = "a".repeat(JobPreparationPolicy.MAX_FAILURE_LENGTH);
		assertEquals(message, JobPreparationPolicy.limitFailure(message));
	}

	@Test
	void longFailureIsCut() {
		String message = "a".repeat(JobPreparationPolicy.MAX_FAILURE_LENGTH + 10);
		assertEquals("a".repeat(JobPreparationPolicy.MAX_FAILURE_LENGTH), JobPreparationPolicy.limitFailure(message));
	}

	@Test
	void nullFailureStaysNull() {
		assertNull(JobPreparationPolicy.limitFailure(null));
	}

}
