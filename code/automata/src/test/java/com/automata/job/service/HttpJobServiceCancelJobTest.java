package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.repository.HttpJobRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class HttpJobServiceCancelJobTest {

	@Mock
	HttpJobRepository jobRepo;

	@InjectMocks
	HttpJobService service;

	private HttpJob jobIn(JobState state) {
		HttpJob job = new WideHttpJob();
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(state).build());
		when(jobRepo.findById(1L)).thenReturn(Optional.of(job));
		return job;
	}

	@Test
	void jobWaitingToBeQueuedIsCanceledAtOnceSoThatTheSyncCanNotQueueIt() {
		HttpJob job = jobIn(JobState.TOQUEUE);

		service.cancelJob(1L);

		assertEquals(JobState.CANCELED, job.getGenericDetails().getCurrentState());
		assertNull(job.getGenericDetails().getRequestedState());
		verify(jobRepo).save(job);
	}

	@Test
	void queuedJobIsAskedToCancelBecauseTheWorkerOwnsItsState() {
		HttpJob job = jobIn(JobState.QUEUED);

		service.cancelJob(1L);

		assertEquals(JobState.QUEUED, job.getGenericDetails().getCurrentState());
		assertEquals(JobState.CANCELED, job.getGenericDetails().getRequestedState());
		verify(jobRepo).save(job);
	}

	@Test
	void pausedJobIsAskedToCancelBecauseTheWorkerOwnsItsState() {
		HttpJob job = jobIn(JobState.PAUSED);

		service.cancelJob(1L);

		assertEquals(JobState.PAUSED, job.getGenericDetails().getCurrentState());
		assertEquals(JobState.CANCELED, job.getGenericDetails().getRequestedState());
		verify(jobRepo).save(job);
	}

	@Test
	void scheduledJobCanNotBeCanceledYet() {
		HttpJob job = jobIn(JobState.SCHEDULED);

		assertThrows(RuntimeException.class, () -> service.cancelJob(1L));

		assertEquals(JobState.SCHEDULED, job.getGenericDetails().getCurrentState());
		verify(jobRepo, never()).save(job);
	}

	@Test
	void runningJobCanNotBeCanceled() {
		HttpJob job = jobIn(JobState.RUNNING);

		assertThrows(RuntimeException.class, () -> service.cancelJob(1L));

		assertNull(job.getGenericDetails().getRequestedState());
		verify(jobRepo, never()).save(job);
	}

	@Test
	void unknownJobIsReported() {
		when(jobRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.cancelJob(1L));
	}

}
