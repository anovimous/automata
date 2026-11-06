package com.automata.job.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.automata.job.domain.model.enums.ConfigFileStatus;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;

class HttpJobUtilsWaitingForActionTest {

	@Test
	void anAiDraftNobodyApprovedWaitsForAHuman() {
		assertTrue(HttpJobUtils.isWaitingForAction(JobState.DRAFT, JobCreator.AI, false, ConfigFileStatus.READY));
	}

	@Test
	void anApprovedAiDraftWithReadyFilesWaitsForNobody() {
		assertFalse(HttpJobUtils.isWaitingForAction(JobState.DRAFT, JobCreator.AI, true, ConfigFileStatus.READY));
	}

	@Test
	void aUserDraftIsNotWaitingBecauseItIsNotApproved() {
		assertFalse(HttpJobUtils.isWaitingForAction(JobState.DRAFT, JobCreator.USER, false, ConfigFileStatus.READY));
	}

	@Test
	void aDraftWithFailedFilesWaitsForARetry() {
		assertTrue(HttpJobUtils.isWaitingForAction(JobState.DRAFT, JobCreator.USER, true, ConfigFileStatus.FAILED));
		assertTrue(HttpJobUtils.isWaitingForAction(JobState.DRAFT, JobCreator.AI, true, ConfigFileStatus.FAILED));
	}

	@Test
	void aDraftWhoseFilesArePreparingIsNotWaiting() {
		assertFalse(HttpJobUtils.isWaitingForAction(JobState.DRAFT, JobCreator.USER, true, ConfigFileStatus.PREPARING));
	}

	@Test
	void nothingIsWaitingOutsideTheDraftState() {
		for (JobState state : JobState.values())
			if (state != JobState.DRAFT)
				assertFalse(HttpJobUtils.isWaitingForAction(state, JobCreator.AI, false, ConfigFileStatus.FAILED),
						state.name());
	}

}
