package com.automata.job.domain.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.automata.job.domain.model.enums.JobState;
import com.automata.job.service.HttpJobStateService;
import com.automata.testsupport.KnownBug;

/**
 * A DRAFT job that nobody wants (an AI job a human does not approve, a job whose files failed for good) has no way out: it cannot be canceled and
 * deleting a job is not implemented, so it stays in the list as "waiting for action" for ever.
 */
class DraftJobDisposalTest {

	@Test
	@KnownBug("BUG-032")
	void aDraftJobCanBeCanceled() {
		assertDoesNotThrow(() -> JobStateTransitions.cancel(JobState.DRAFT, null));
	}

	@Test
	void aDraftJobIsNotCancelableToday() {
		assertThrows(com.automata.job.domain.exception.InvalidJobStateException.class, () -> JobStateTransitions.cancel(JobState.DRAFT, null));
	}

	@Test
	void deletingAJobIsNotImplementedToday() {
		HttpJobStateService service = new HttpJobStateService(null, null);

		assertThrows(UnsupportedOperationException.class, () -> service.deleteJob(1L));
	}
}
