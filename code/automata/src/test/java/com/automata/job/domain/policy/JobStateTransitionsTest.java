package com.automata.job.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.policy.JobStateTransitions.ToApplyJobStateChange;

class JobStateTransitionsTest {

	// ---- prepareForQueueing

	@Test
	void aDraftJobIsPreparedForQueueing() {
		assertEquals(new ToApplyJobStateChange(JobState.TOQUEUE, null),
				JobStateTransitions.prepareForQueueing(JobState.DRAFT, null));
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
	void onlyADraftJobCanBePreparedForQueueing(JobState current) {
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
				() -> JobStateTransitions.prepareForQueueing(current, null));
		assertEquals("The job must be in the DRAFT state in order to prepare it for queueing, but it is " + current,
				ex.getMessage());
	}

	// ---- requestPause / requestResume

	@Test
	void aRunningJobIsRequestedToPauseWithoutChangingItsCurrentState() {
		assertEquals(new ToApplyJobStateChange(JobState.RUNNING, JobState.PAUSED),
				JobStateTransitions.requestPause(JobState.RUNNING, null));
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = "RUNNING", mode = EnumSource.Mode.EXCLUDE)
	void onlyARunningJobCanBePaused(JobState current) {
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
				() -> JobStateTransitions.requestPause(current, null));
		assertEquals("The job must be in the RUNNING state in order to pause it, but it is " + current,
				ex.getMessage());
	}

	@Test
	void aPausedJobIsRequestedToResumeWithoutChangingItsCurrentState() {
		assertEquals(new ToApplyJobStateChange(JobState.PAUSED, JobState.RUNNING),
				JobStateTransitions.requestResume(JobState.PAUSED, null));
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = "PAUSED", mode = EnumSource.Mode.EXCLUDE)
	void onlyAPausedJobCanBeResumed(JobState current) {
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
				() -> JobStateTransitions.requestResume(current, null));
		assertEquals("The job must be in the PAUSED state in order to resume it, but it is " + current,
				ex.getMessage());
	}

	@Test
	void aNewPauseRequestReplacesAPendingRequest() {
		assertEquals(JobState.PAUSED, JobStateTransitions.requestPause(JobState.RUNNING, JobState.CANCELED).toApplyRequestedState());
	}

	// ---- cancel

	@Test
	void aJobWaitingToBeQueuedIsCanceledAtOnce() {
		assertEquals(new ToApplyJobStateChange(JobState.CANCELED, null), JobStateTransitions.cancel(JobState.TOQUEUE, null));
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "QUEUED", "PAUSED" })
	void aJobHeldByAWorkerIsOnlyRequestedToCancel(JobState current) {
		assertEquals(new ToApplyJobStateChange(current, JobState.CANCELED), JobStateTransitions.cancel(current, null));
	}

	@Test
	void canceledScheduledJobsAreNotImplemented() {
		assertThrows(UnsupportedOperationException.class, () -> JobStateTransitions.cancel(JobState.SCHEDULED, null));
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "TOQUEUE", "SCHEDULED", "QUEUED", "PAUSED" }, mode = EnumSource.Mode.EXCLUDE)
	void otherJobsCannotBeCanceled(JobState current) {
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
				() -> JobStateTransitions.cancel(current, null));
		assertEquals(
				"The job must be in the [TOQUEUE,SCHEDULED,QUEUED,PAUSED] states in order to cancel it, but it is "
						+ current,
				ex.getMessage());
	}

	// ---- reportStateAppliedByWorker

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "RUNNING", "PAUSED", "CANCELED", "FINISHED", "FAILED",
			"TERMINATED" })
	void aWorkerReportSetsTheCurrentStateAndClearsTheRequestedOne(JobState reported) {
		assertEquals(new ToApplyJobStateChange(reported, null),
				JobStateTransitions.reportStateAppliedByWorker(JobState.RUNNING, JobState.PAUSED, reported));
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "DRAFT", "TOQUEUE", "SCHEDULED", "QUEUED" })
	void aWorkerCannotReportTheStatesOfTheOrchestrator(JobState reported) {
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
				() -> JobStateTransitions.reportStateAppliedByWorker(JobState.RUNNING, null, reported));
		assertEquals("The job is not allowed to this state by the orchestrator", ex.getMessage());
	}

	@Test
	void theWorkerCurrentlyMayReportFromAnyCurrentState() {
		Set<JobState> reportable = EnumSet.of(JobState.RUNNING, JobState.PAUSED, JobState.CANCELED,
				JobState.FINISHED, JobState.FAILED, JobState.TERMINATED);
		for (JobState current : JobState.values())
			for (JobState reported : reportable)
				assertTrue(JobStateTransitions.reportStateAppliedByWorker(current, null, reported).toApplyCurrentState() == reported);
	}

}
