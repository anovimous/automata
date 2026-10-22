package com.automata.job.infra;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.job.service.HttpJobStateService;
import com.automata.job.service.HttpJobsSynchronizationService;

@ExtendWith(MockitoExtension.class)
class SpringSchedulerTest {

	@Mock
	HttpJobsSynchronizationService syncService;

	@Mock
	HttpJobStateService stateService;

	@InjectMocks
	SpringScheduler scheduler;

	@Test
	void synchronizeRunsTheSync() {
		scheduler.synchronizeHttpJobs();

		verify(syncService).syncToQueueJobs();
	}

	@Test
	void jobsWithLostContactAreMarkedWithTheConfiguredTime() {
		scheduler.markJobsWithLostContact();

		verify(stateService).markJobsWithLostContact(any(Instant.class), eq(HttpJobStateService.LOST_CONTACT_AFTER));
	}

	@Test
	void aFailureWhileMarkingDoesNotEscapeTheScheduler() {
		doThrow(new RuntimeException("job changed")).when(stateService).markJobsWithLostContact(any(), any());

		scheduler.markJobsWithLostContact();

		verify(stateService).markJobsWithLostContact(any(), any());
	}

}
