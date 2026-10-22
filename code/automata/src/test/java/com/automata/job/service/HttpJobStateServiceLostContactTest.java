package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
class HttpJobStateServiceLostContactTest {

	private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

	@Mock
	HttpJobRepository jobRepo;

	@InjectMocks
	HttpJobStateService service;

	private HttpJob jobIn(JobState state, JobState requested) {
		HttpJob job = new WideHttpJob();
		job.setId(1L);
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(state).requestedState(requested).build());
		return job;
	}

	// ---- recordWorkerContact

	@Test
	void workerContactIsRecordedWithoutLoadingTheJob() {
		when(jobRepo.updateLastWorkerContact(eq(1L), any(Instant.class))).thenReturn(1);

		service.recordWorkerContact(1L);

		verify(jobRepo).updateLastWorkerContact(eq(1L), any(Instant.class));
		verify(jobRepo, never()).findById(any());
	}

	@Test
	void contactAboutAnUnknownJobIsReported() {
		when(jobRepo.updateLastWorkerContact(eq(1L), any(Instant.class))).thenReturn(0);

		assertThrows(EntityNotFoundException.class, () -> service.recordWorkerContact(1L));
	}

	// ---- markJobsWithLostContact

	@Test
	void jobsWithoutContactForTheGivenTimeAreLookedUpByTheThreshold() {
		Duration after = Duration.ofMinutes(30);
		when(jobRepo.findAllByCurrentStateInAndLastWorkerContactBefore(any(), any())).thenReturn(List.of());

		int moved = service.markJobsWithLostContact(NOW, after);

		assertEquals(0, moved);
		verify(jobRepo).findAllByCurrentStateInAndLastWorkerContactBefore(
				List.of(JobState.RUNNING, JobState.PAUSED), NOW.minus(after));
		verify(jobRepo, never()).save(any());
	}

	@Test
	void lostJobsAreMovedToLostContactWithTheirRequestDroppedAndAnExplanation() {
		HttpJob running = jobIn(JobState.RUNNING, JobState.PAUSED);
		HttpJob paused = jobIn(JobState.PAUSED, null);
		when(jobRepo.findAllByCurrentStateInAndLastWorkerContactBefore(any(), any()))
				.thenReturn(List.of(running, paused));

		int moved = service.markJobsWithLostContact(NOW, Duration.ofMinutes(30));

		assertEquals(2, moved);
		for (HttpJob job : List.of(running, paused)) {
			assertEquals(JobState.LOSTCONTACT, job.getGenericDetails().getCurrentState());
			assertNull(job.getGenericDetails().getRequestedState());
			assertNotNull(job.getResult());
			assertTrue(job.getResult().getFailureMsg().contains("30 minutes"));
			verify(jobRepo).save(job);
		}
	}

	@Test
	void aJobWhichIsNotHeldByAWorkerAnymoreIsNotMoved() {
		HttpJob finished = jobIn(JobState.FINISHED, null);
		when(jobRepo.findAllByCurrentStateInAndLastWorkerContactBefore(any(), any())).thenReturn(List.of(finished));

		assertThrows(com.automata.job.domain.exception.InvalidJobStateException.class,
				() -> service.markJobsWithLostContact(NOW, Duration.ofMinutes(30)));

		assertEquals(JobState.FINISHED, finished.getGenericDetails().getCurrentState());
		verify(jobRepo, never()).save(any());
	}

}
