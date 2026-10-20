package com.automata.job.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.Map;
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
	@EnumSource(value = JobState.class, names = { "QUEUED", "RUNNING", "PAUSED" })
	void aJobHeldByAWorkerIsOnlyRequestedToCancel(JobState current) {
		assertEquals(new ToApplyJobStateChange(current, JobState.CANCELED), JobStateTransitions.cancel(current, null));
	}

	@Test
	void canceledScheduledJobsAreNotImplemented() {
		assertThrows(UnsupportedOperationException.class, () -> JobStateTransitions.cancel(JobState.SCHEDULED, null));
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "TOQUEUE", "SCHEDULED", "QUEUED", "RUNNING", "PAUSED" }, mode = EnumSource.Mode.EXCLUDE)
	void otherJobsCannotBeCanceled(JobState current) {
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
				() -> JobStateTransitions.cancel(current, null));
		assertEquals(
				"The job must be in the [TOQUEUE,SCHEDULED,QUEUED,RUNNING,PAUSED] states in order to cancel it, but it is "
						+ current,
				ex.getMessage());
	}

	// ---- reportStateAppliedByWorker

	private static final Map<JobState, Set<JobState>> ALLOWED_REPORTS = Map.of(
			JobState.QUEUED, EnumSet.of(JobState.RUNNING, JobState.CANCELED, JobState.FAILED, JobState.SELFTERMINATED),
			JobState.RUNNING, EnumSet.of(JobState.PAUSED, JobState.CANCELED, JobState.FINISHED, JobState.FAILED,
					JobState.SELFTERMINATED),
			JobState.PAUSED, EnumSet.of(JobState.RUNNING, JobState.CANCELED, JobState.SELFTERMINATED));

	private static final Set<JobState> REPORTABLE = EnumSet.of(JobState.RUNNING, JobState.PAUSED,
			JobState.CANCELED, JobState.FINISHED, JobState.FAILED, JobState.SELFTERMINATED);

	@Test
	void aWorkerReportSetsTheCurrentStateAndClearsTheRequestedOne() {
		assertEquals(new ToApplyJobStateChange(JobState.PAUSED, null),
				JobStateTransitions.reportStateAppliedByWorker(JobState.RUNNING, JobState.PAUSED, JobState.PAUSED));
	}

	@Test
	void everyReportAllowedByTheTableIsAccepted() {
		ALLOWED_REPORTS.forEach((current, reportedStates) -> reportedStates.forEach(reported -> assertEquals(
				new ToApplyJobStateChange(reported, null),
				JobStateTransitions.reportStateAppliedByWorker(current, requestedFor(reported), reported),
				current + " -> " + reported)));
	}

	// what the user must have requested for the worker to report the state
	private static JobState requestedFor(JobState reported) {
		return reported == JobState.PAUSED || reported == JobState.CANCELED || reported == JobState.RUNNING
				? reported
				: null;
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "PAUSED", "CANCELED" })
	void pausingAndCancelingAreOnlyReportedWhenTheyWereRequested(JobState reported) {
		JobState current = reported == JobState.PAUSED ? JobState.RUNNING : JobState.QUEUED;
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
				() -> JobStateTransitions.reportStateAppliedByWorker(current, null, reported));
		assertEquals("A worker can only report the state " + reported
				+ " for a job for which it was requested, but nothing is requested", ex.getMessage());

		JobState other = reported == JobState.PAUSED ? JobState.CANCELED : JobState.PAUSED;
		ex = assertThrows(InvalidJobStateException.class,
				() -> JobStateTransitions.reportStateAppliedByWorker(current, other, reported));
		assertEquals("A worker can only report the state " + reported
				+ " for a job for which it was requested, but " + other + " is requested", ex.getMessage());
	}

	@Test
	void aRunningJobIsReportedCanceledOnlyWhenItWasRequested() {
		assertEquals(new ToApplyJobStateChange(JobState.CANCELED, null),
				JobStateTransitions.reportStateAppliedByWorker(JobState.RUNNING, JobState.CANCELED, JobState.CANCELED));

		assertThrows(InvalidJobStateException.class, () -> JobStateTransitions
				.reportStateAppliedByWorker(JobState.RUNNING, null, JobState.CANCELED));
	}

	@Test
	void resumingIsOnlyReportedWhenItWasRequested() {
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
				() -> JobStateTransitions.reportStateAppliedByWorker(JobState.PAUSED, null, JobState.RUNNING));
		assertEquals("A worker can only report the state RUNNING for a job for which it was requested, but "
				+ "nothing is requested", ex.getMessage());

		ex = assertThrows(InvalidJobStateException.class, () -> JobStateTransitions
				.reportStateAppliedByWorker(JobState.PAUSED, JobState.CANCELED, JobState.RUNNING));
		assertEquals("A worker can only report the state RUNNING for a job for which it was requested, but "
				+ "CANCELED is requested", ex.getMessage());
	}

	@Test
	void aQueuedJobIsStartedWithoutAnyRequest() {
		assertEquals(new ToApplyJobStateChange(JobState.RUNNING, null),
				JobStateTransitions.reportStateAppliedByWorker(JobState.QUEUED, null, JobState.RUNNING));
	}

	@Test
	void otherStatesAreReportedWhateverIsRequested() {
		assertEquals(new ToApplyJobStateChange(JobState.FINISHED, null),
				JobStateTransitions.reportStateAppliedByWorker(JobState.RUNNING, JobState.PAUSED, JobState.FINISHED));
	}

	@Test
	void everyReportOutsideTheTableIsRejected() {
		for (JobState current : JobState.values())
			for (JobState reported : REPORTABLE)
				if (!ALLOWED_REPORTS.getOrDefault(current, Set.of()).contains(reported)
						&& !isAcceptedRepeat(current, reported)) {
					InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
							() -> JobStateTransitions.reportStateAppliedByWorker(current, null, reported),
							current + " -> " + reported);
					assertEquals(
							"A worker can't report the state " + reported + " for a job which is " + current,
							ex.getMessage());
				}
	}

	private static boolean isAcceptedRepeat(JobState current, JobState reported) {
		return current == reported && reported != JobState.RUNNING;
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "DRAFT", "TOQUEUE", "SCHEDULED", "FINISHED", "FAILED", "CANCELED",
			"SELFTERMINATED" })
	void nothingNewCanBeReportedForAJobNoWorkerHoldsOrWhichEnded(JobState current) {
		for (JobState reported : REPORTABLE)
			if (!isAcceptedRepeat(current, reported))
				assertThrows(InvalidJobStateException.class,
						() -> JobStateTransitions.reportStateAppliedByWorker(current, null, reported));
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "PAUSED", "FINISHED", "FAILED", "CANCELED", "SELFTERMINATED" })
	void aRepeatedReportIsAcceptedAndChangesNothing(JobState state) {
		assertEquals(new ToApplyJobStateChange(state, JobState.RUNNING),
				JobStateTransitions.reportStateAppliedByWorker(state, JobState.RUNNING, state));
		assertEquals(new ToApplyJobStateChange(state, null),
				JobStateTransitions.reportStateAppliedByWorker(state, null, state));
	}

	@Test
	void aRepeatedRunningIsRejected() {
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class, () -> JobStateTransitions
				.reportStateAppliedByWorker(JobState.RUNNING, JobState.PAUSED, JobState.RUNNING));
		assertEquals("A worker can't report the state RUNNING for a job which is RUNNING", ex.getMessage());
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "DRAFT", "TOQUEUE", "SCHEDULED", "QUEUED" })
	void aWorkerCannotReportTheStatesOfTheOrchestrator(JobState reported) {
		InvalidJobStateException ex = assertThrows(InvalidJobStateException.class,
				() -> JobStateTransitions.reportStateAppliedByWorker(JobState.RUNNING, null, reported));
		assertEquals("The job is not allowed to this state by the orchestrator", ex.getMessage());
	}

}
