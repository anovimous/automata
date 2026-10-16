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

import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.repository.HttpJobRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class HttpJobServiceUpdateStateTest {

	@Mock
	HttpJobRepository jobRepo;

	@InjectMocks
	HttpJobService service;

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

		service.updateCurrentJobState(1L, JobState.RUNNING, "started");

		assertEquals(JobState.RUNNING, job.getGenericDetails().getCurrentState());
		assertNull(job.getGenericDetails().getRequestedState());
		verify(jobRepo).save(job);
	}

	@Test
	void workerCanNotMoveTheJobToAStateOwnedByTheOrchestrator() {
		HttpJob job = job();

		assertThrows(InvalidJobStateException.class,
				() -> service.updateCurrentJobState(1L, JobState.QUEUED, "nope"));

		assertEquals(JobState.QUEUED, job.getGenericDetails().getCurrentState());
		verify(jobRepo, never()).save(job);
	}

	@Test
	void unknownJobIsReported() {
		when(jobRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class,
				() -> service.updateCurrentJobState(1L, JobState.RUNNING, null));
	}

}
