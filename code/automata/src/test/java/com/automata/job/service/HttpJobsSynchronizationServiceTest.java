package com.automata.job.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.host.HostRepository;
import com.automata.host.common.dto.HostAllRateLimitsInternalDto;
import com.automata.host.common.dto.HostRateLimitInternalDto;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobInternalDto;
import com.automata.job.domain.valueobject.HttpJobRoutineInternalDto;
import com.automata.job.domain.valueobject.ProgramCurrentWideRateDto;
import com.automata.job.domain.valueobject.ProgramRateDto;
import com.automata.job.infra.JobQueueService;
import com.automata.job.repository.HttpJobRepository;
import com.automata.job.repository.NarrowHttpJobRepository;
import com.automata.job.repository.WideHttpJobRepository;
import com.automata.program.ProgramRepository;
import com.automata.routine.common.enums.Duration;

/**
 * The database only learns about the jobs queued in a run after the loop, so
 * these tests check that every accepted job is still counted by the jobs which
 * come after it in the same run.
 */
@ExtendWith(MockitoExtension.class)
class HttpJobsSynchronizationServiceTest {

	private static final Long PROGRAM = 1L;

	@Mock
	HttpJobRepository jobRepo;
	@Mock
	NarrowHttpJobRepository narrowJobRepo;
	@Mock
	WideHttpJobRepository wideJobRepo;
	@Mock
	ProgramRepository programRepo;
	@Mock
	HostRepository hostRepo;
	@Mock
	JobQueueService queueService;

	HttpJobsSynchronizationService service;

	@BeforeEach
	void setUp() {
		service = new HttpJobsSynchronizationService(jobRepo, narrowJobRepo, wideJobRepo, programRepo,
				new JobDispatchPlanner(narrowJobRepo, hostRepo), queueService);
	}

	// The higher the priority, the earlier the job is handled; the tests give the
	// jobs priorities 3, 2, 1 in the order they are meant to be handled.
	private static HttpJobInternalDto narrow(long id, int priority, int rate, long hostId, Duration duration) {
		return new HttpJobInternalDto(id, Instant.EPOCH, HttpJobScope.NARROW, duration, priority, rate, PROGRAM,
				hostId);
	}

	private static HttpJobInternalDto wide(long id, int priority, int rate) {
		return new HttpJobInternalDto(id, Instant.EPOCH, HttpJobScope.WIDE, null, priority, rate, PROGRAM, null);
	}

	private void givenToQueue(int programRateLimit, List<HttpJobInternalDto> narrowJobs,
			List<HttpJobInternalDto> wideJobs) {
		when(narrowJobRepo.getJobsDtosByCurrentState(JobState.TOQUEUE)).thenReturn(narrowJobs);
		when(wideJobRepo.getJobsDtosByCurrentState(JobState.TOQUEUE)).thenReturn(wideJobs);
		lenient().when(jobRepo.findJobsRoutineKeys(any())).thenAnswer(invocation -> {
			List<Long> ids = invocation.getArgument(0);
			return ids.stream().map(id -> new HttpJobRoutineInternalDto(id, "routine")).toList();
		});
		when(jobRepo.getProgramsRateDtos(any(), any()))
				.thenReturn(List.of(new ProgramRateDto(PROGRAM, programRateLimit, 0L)));
		when(wideJobRepo.getProgramsWideRateDtos(any(), any()))
				.thenReturn(List.of(new ProgramCurrentWideRateDto(PROGRAM, 0L)));
	}

	private void givenHostLimits(long hostId, int rateLimit, int shortLimit, int longLimit) {
		when(hostRepo.findRateLimitsAllDtoById(hostId))
				.thenReturn(new HostAllRateLimitsInternalDto(hostId, rateLimit, shortLimit, longLimit));
	}

	private void thenQueuedExactly(Long... ids) {
		verify(queueService, times(ids.length)).queueHttpJob(any(), anyString());
		verify(jobRepo).updateJobsState(Set.of(ids), JobState.QUEUED, JobState.TOQUEUE);
	}

	@Test
	void narrowJobsWithinTheProgramLimitAreAllQueued() {
		HttpJobInternalDto first = narrow(1, 3, 40, 10, Duration.SHORT);
		HttpJobInternalDto second = narrow(2, 2, 40, 11, Duration.SHORT);
		givenToQueue(100, List.of(first, second), List.of());
		givenHostLimits(10, 1000, 1000, 1000);
		givenHostLimits(11, 1000, 1000, 1000);

		service.syncToQueueJobs();

		thenQueuedExactly(1L, 2L);
	}

	@Test
	void higherPriorityJobGetsTheRemainingCapacityBeforeAnOlderLowerPriorityJob() {
		HttpJobInternalDto olderLowPriority = new HttpJobInternalDto(1L, Instant.ofEpochSecond(0), HttpJobScope.NARROW,
				Duration.SHORT, 1, 60, PROGRAM, 10L);
		HttpJobInternalDto newerHighPriority = new HttpJobInternalDto(2L, Instant.ofEpochSecond(9),
				HttpJobScope.NARROW, Duration.SHORT, 3, 60, PROGRAM, 11L);
		givenToQueue(100, List.of(olderLowPriority, newerHighPriority), List.of());
		givenHostLimits(11, 1000, 1000, 1000);

		service.syncToQueueJobs();

		verify(queueService).queueHttpJob(eq(newerHighPriority), anyString());
		verify(queueService, never()).queueHttpJob(eq(olderLowPriority), anyString());
		thenQueuedExactly(2L);
	}

	@Test
	void jobAcceptedEarlierInTheRunCountsAgainstTheProgramLimit() {
		HttpJobInternalDto first = narrow(1, 3, 60, 10, Duration.SHORT);
		HttpJobInternalDto second = narrow(2, 2, 60, 11, Duration.SHORT);
		givenToQueue(100, List.of(first, second), List.of());
		givenHostLimits(10, 1000, 1000, 1000);

		service.syncToQueueJobs();

		verify(queueService).queueHttpJob(eq(first), anyString());
		verify(queueService, never()).queueHttpJob(eq(second), anyString());
		thenQueuedExactly(1L);
	}

	@Test
	void narrowJobAcceptedEarlierCountsAgainstTheHostRateLimit() {
		HttpJobInternalDto first = narrow(1, 3, 60, 10, Duration.SHORT);
		HttpJobInternalDto second = narrow(2, 2, 60, 10, Duration.SHORT);
		givenToQueue(1000, List.of(first, second), List.of());
		givenHostLimits(10, 100, 1000, 1000);

		service.syncToQueueJobs();

		thenQueuedExactly(1L);
	}

	@Test
	void narrowJobAcceptedEarlierCountsAgainstTheMatchingDurationLimitOnly() {
		HttpJobInternalDto firstShort = narrow(1, 3, 60, 10, Duration.SHORT);
		HttpJobInternalDto secondShort = narrow(2, 2, 60, 10, Duration.SHORT);
		HttpJobInternalDto firstLong = narrow(3, 1, 60, 10, Duration.LONG);
		givenToQueue(1000, List.of(firstShort, secondShort, firstLong), List.of());
		givenHostLimits(10, 1000, 100, 100);

		service.syncToQueueJobs();

		thenQueuedExactly(1L, 3L);
	}

	@Test
	void wideJobAcceptedEarlierCountsAgainstLaterNarrowJobsOnEveryHost() {
		HttpJobInternalDto wideJob = wide(1, 3, 70);
		HttpJobInternalDto narrowJob = narrow(2, 2, 40, 10, Duration.SHORT);
		givenToQueue(1000, List.of(narrowJob), List.of(wideJob));
		givenHostLimits(10, 100, 1000, 1000);

		service.syncToQueueJobs();

		verify(queueService).queueHttpJob(eq(wideJob), anyString());
		verify(queueService, never()).queueHttpJob(eq(narrowJob), anyString());
		thenQueuedExactly(1L);
	}

	@Test
	void wideJobAcceptedEarlierCountsAgainstLaterWideJobsEvenOnHostsWithoutNarrowJobs() {
		HttpJobInternalDto first = wide(1, 3, 70);
		HttpJobInternalDto second = wide(2, 2, 70);
		givenToQueue(1000, List.of(), List.of(first, second));
		// some host of the program has a limit of 100: it fits one wide job, not two
		when(hostRepo.anyInScopeProgramHostHasRateLimitLessThanGivenRate(PROGRAM, 70)).thenReturn(false);
		when(hostRepo.anyInScopeProgramHostHasRateLimitLessThanGivenRate(PROGRAM, 140)).thenReturn(true);

		service.syncToQueueJobs();

		verify(queueService).queueHttpJob(eq(first), anyString());
		verify(queueService, never()).queueHttpJob(eq(second), anyString());
		thenQueuedExactly(1L);
	}

	@Test
	void narrowJobAcceptedEarlierCountsAgainstLaterWideJobsOnItsHost() {
		HttpJobInternalDto narrowJob = narrow(1, 3, 60, 10, Duration.SHORT);
		HttpJobInternalDto wideJob = wide(2, 2, 50);
		givenToQueue(1000, List.of(narrowJob), List.of(wideJob));
		givenHostLimits(10, 100, 1000, 1000);
		when(hostRepo.findDtosByIds(Set.of(10L))).thenReturn(List.of(new HostRateLimitInternalDto(10L, 100)));

		service.syncToQueueJobs();

		verify(queueService).queueHttpJob(eq(narrowJob), anyString());
		verify(queueService, never()).queueHttpJob(eq(wideJob), anyString());
		thenQueuedExactly(1L);
	}

}
