package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.JobResult;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.FailureCategory;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.repository.HttpJobRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class HttpJobStateServiceUpdateStateTest {

	@Mock
	HttpJobRepository jobRepo;

	@InjectMocks
	HttpJobStateService service;

	private HttpJob job() {
		HttpJob job = new WideHttpJob();
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(JobState.QUEUED)
				.requestedState(JobState.CANCELED).build());
		when(jobRepo.findById(1L)).thenReturn(Optional.of(job));
		return job;
	}

	@Test
	void workerStateIsStoredAndTheRequestedStateCleared() {
		HttpJob job = job();

		service.updateCurrentJobState(1L, JobState.RUNNING, "started", null);

		assertEquals(JobState.RUNNING, job.getGenericDetails().getCurrentState());
		assertNull(job.getGenericDetails().getRequestedState());
		verify(jobRepo).save(job);
	}

	@Test
	void acceptedReportCountsAsContactOfTheWorker() {
		HttpJob job = job();

		service.updateCurrentJobState(1L, JobState.RUNNING, null, null);

		assertNotNull(job.getGenericDetails().getLastWorkerContactAt());
	}

	@Test
	void rejectedReportIsNotContactOfTheWorker() {
		HttpJob job = job();

		assertThrows(InvalidJobStateException.class, () -> service.updateCurrentJobState(1L, JobState.TOQUEUE, null, null));

		assertNull(job.getGenericDetails().getLastWorkerContactAt());
	}

	@Test
	void workerCanNotMoveTheJobToAStateOwnedByTheOrchestrator() {
		HttpJob job = job();

		assertThrows(InvalidJobStateException.class,
				() -> service.updateCurrentJobState(1L, JobState.QUEUED, "nope", null));

		assertEquals(JobState.QUEUED, job.getGenericDetails().getCurrentState());
		verify(jobRepo, never()).save(job);
	}

	@Test
	void unknownJobIsReported() {
		when(jobRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class,
				() -> service.updateCurrentJobState(1L, JobState.RUNNING, null, null));
	}

	private HttpJob runningJob() {
		HttpJob job = new WideHttpJob();
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(JobState.RUNNING).build());
		when(jobRepo.findById(1L)).thenReturn(Optional.of(job));
		return job;
	}

	@Test
	void failureMessageIsStoredWhenTheJobFails() {
		HttpJob job = runningJob();

		service.updateCurrentJobState(1L, JobState.FAILED, "target refused the connection", null);

		assertEquals("target refused the connection", job.getResult().getFailureMsg());
	}

	@Test
	void terminationMessageIsStoredToo() {
		HttpJob job = runningJob();

		service.updateCurrentJobState(1L, JobState.SELFTERMINATED, "worker lost its brain", null);

		assertEquals("worker lost its brain", job.getResult().getFailureMsg());
	}

	@Test
	void messageOfAStateWhichIsNotAFailureIsNotStored() {
		HttpJob job = runningJob();

		service.updateCurrentJobState(1L, JobState.FINISHED, "done", null);

		assertNull(job.getResult());
	}

	@Test
	void missingOrBlankFailureMessageStoresNothing() {
		HttpJob job = runningJob();

		service.updateCurrentJobState(1L, JobState.FAILED, "  ", null);

		assertNull(job.getResult());
	}

	@Test
	void longFailureMessageIsCutToWhatTheColumnHolds() {
		HttpJob job = runningJob();

		service.updateCurrentJobState(1L, JobState.FAILED, "x".repeat(JobResult.MAX_FAILURE_MSG_LENGTH + 50), null);

		assertEquals(JobResult.MAX_FAILURE_MSG_LENGTH, job.getResult().getFailureMsg().length());
	}

	@Test
	void repeatedFailureReportKeepsTheFirstMessage() {
		HttpJob job = runningJob();
		service.updateCurrentJobState(1L, JobState.FAILED, "first", null);

		service.updateCurrentJobState(1L, JobState.FAILED, "second", null);

		assertEquals("first", job.getResult().getFailureMsg());
	}

	@Test
	void failureCategoryIsStoredWithTheMessage() {
		HttpJob job = runningJob();

		service.updateCurrentJobState(1L, JobState.FAILED, "tool exited", FailureCategory.TOOL_EXIT);

		assertEquals(FailureCategory.TOOL_EXIT, job.getResult().getFailureCategory());
		assertEquals("tool exited", job.getResult().getFailureMsg());
	}

	@Test
	void aMessageWithoutACategoryIsStoredAsUnknown() {
		HttpJob job = runningJob();

		service.updateCurrentJobState(1L, JobState.FAILED, "something", null);

		assertEquals(FailureCategory.UNKNOWN, job.getResult().getFailureCategory());
	}

	@Test
	void aCategoryWithoutAMessageIsStoredToo() {
		HttpJob job = runningJob();

		service.updateCurrentJobState(1L, JobState.FAILED, " ", FailureCategory.RESULT);

		assertEquals(FailureCategory.RESULT, job.getResult().getFailureCategory());
		assertNull(job.getResult().getFailureMsg());
	}

	@Test
	void theCategoryOfASelfTerminatedJobIsStored() {
		HttpJob job = runningJob();

		service.updateCurrentJobState(1L, JobState.SELFTERMINATED, "no heartbeat", FailureCategory.TOOL_EXIT);

		assertEquals(FailureCategory.TOOL_EXIT, job.getResult().getFailureCategory());
	}

	@Test
	void theCategoryOfAStateWhichIsNotAFailureIsNotStored() {
		HttpJob job = runningJob();

		service.updateCurrentJobState(1L, JobState.FINISHED, "done", FailureCategory.TOOL_EXIT);

		assertNull(job.getResult());
	}

	@Test
	void repeatedFailureReportKeepsTheFirstCategory() {
		HttpJob job = runningJob();
		service.updateCurrentJobState(1L, JobState.FAILED, "first", FailureCategory.POLL);

		service.updateCurrentJobState(1L, JobState.FAILED, "second", FailureCategory.TOOL_EXIT);

		assertEquals(FailureCategory.POLL, job.getResult().getFailureCategory());
		assertEquals("first", job.getResult().getFailureMsg());
	}

	@Test
	void rejectedReportStoresNoMessage() {
		// a paused job can't fail, only be self-terminated
		HttpJob job = new WideHttpJob();
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(JobState.PAUSED).build());
		when(jobRepo.findById(1L)).thenReturn(Optional.of(job));

		assertThrows(InvalidJobStateException.class, () -> service.updateCurrentJobState(1L, JobState.FAILED, "x", null));

		assertNull(job.getResult());
	}

}
