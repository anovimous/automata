package com.automata.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobInternalDto;
import com.automata.job.infra.JobQueueService;
import com.automata.job.repository.HttpJobRepository;
import com.automata.job.repository.NarrowHttpJobRepository;
import com.automata.job.repository.WideHttpJobRepository;
import com.automata.job.service.HttpJobStateService;
import com.automata.job.service.HttpJobsSynchronizationService;
import com.automata.job.service.JobConfigFileAsyncService;
import com.automata.job.service.JobDispatchPlanner;
import com.automata.program.Program;
import com.automata.routine.Routine;
import com.automata.routine.common.enums.Duration;

/**
 * The queueing of the jobs against a real database: the rate queries, the planner and the state update together. Only the message broker is
 * replaced.
 */
@RepositoryIntegrationTest
@Import({ HttpJobsSynchronizationService.class, JobDispatchPlanner.class, HttpJobStateService.class })
class JobQueueingIntegrationTest {

	@MockitoBean
	JobQueueService queueService;

	@MockitoBean
	JobConfigFileAsyncService configFileService;

	@Autowired
	TestEntityManager em;

	@Autowired
	HttpJobsSynchronizationService syncService;

	@Autowired
	HttpJobStateService stateService;

	@Autowired
	HttpJobRepository jobRepo;

	@Autowired
	NarrowHttpJobRepository narrowRepo;

	@Autowired
	WideHttpJobRepository wideRepo;

	@Autowired
	HostRepository hostRepo;

	Fixtures fx;

	Program program;

	Host host;

	Routine routine;

	@BeforeEach
	void setUp() {
		fx = new Fixtures(em);
		program = fx.program("Acme");
		program.setProgramRateLimit(10);
		fx.persist(program);
		host = fx.host(program, "api.example.com");
		host.setHostRateLimit(10);
		host.setShortRateLimit(4);
		host.setLongRateLimit(6);
		fx.persist(host);
		routine = fx.routine("fuzz");
	}

	private JobState stateOf(HttpJob job) {
		fx.clear();
		return jobRepo.findById(job.getId()).orElseThrow().getGenericDetails().getCurrentState();
	}

	private void sync() {
		fx.clear();
		syncService.syncToQueueJobs();
		fx.clear();
	}

	// ---- the idle minute ----

	@Test
	void aSyncWithNothingToQueueDoesNothingAndDoesNotFail() {
		sync();

		verify(queueService, never()).queueHttpJob(any(), any());
	}

	// ---- narrow jobs ----

	@Test
	void jobsWithinTheLimitsAreQueuedWithTheirRoutineKey() {
		NarrowHttpJob a = fx.narrowJob(routine, host, JobState.TOQUEUE, 2, Duration.SHORT, 1);
		NarrowHttpJob b = fx.narrowJob(routine, host, JobState.TOQUEUE, 3, Duration.LONG, 1);

		sync();

		assertEquals(JobState.QUEUED, stateOf(a));
		assertEquals(JobState.QUEUED, stateOf(b));
		ArgumentCaptor<HttpJobInternalDto> queued = ArgumentCaptor.forClass(HttpJobInternalDto.class);
		verify(queueService, org.mockito.Mockito.times(2)).queueHttpJob(queued.capture(), eq("fuzz"));
		assertEquals(Set.of(a.getId(), b.getId()), Set.of(queued.getAllValues().get(0).id(), queued.getAllValues().get(1).id()));
	}

	@Test
	void jobsInOtherStatesAreNotTouched() {
		NarrowHttpJob draft = fx.narrowJob(routine, host, JobState.DRAFT, 1, Duration.SHORT, 1);
		NarrowHttpJob running = fx.narrowJob(routine, host, JobState.RUNNING, 1, Duration.SHORT, 1);

		sync();

		assertEquals(JobState.DRAFT, stateOf(draft));
		assertEquals(JobState.RUNNING, stateOf(running));
		verify(queueService, never()).queueHttpJob(any(), any());
	}

	@Test
	void theRateOfTheJobsAlreadyRunningCountsAgainstTheHostLongLimit() {
		fx.narrowJob(routine, host, JobState.RUNNING, 5, Duration.LONG, 1);
		NarrowHttpJob fits = fx.narrowJob(routine, host, JobState.TOQUEUE, 1, Duration.LONG, 1);
		NarrowHttpJob tooMuch = fx.narrowJob(routine, host, JobState.TOQUEUE, 2, Duration.LONG, 1);

		sync();

		// long limit 6: 5 running + 1 = 6 fits, but the next one (+2) does not
		assertEquals(JobState.QUEUED, stateOf(fits));
		assertEquals(JobState.TOQUEUE, stateOf(tooMuch));
	}

	@Test
	void shortJobsAreCheckedAgainstTheShortLimit() {
		NarrowHttpJob fits = fx.narrowJob(routine, host, JobState.TOQUEUE, 4, Duration.SHORT, 1);
		NarrowHttpJob tooMuch = fx.narrowJob(routine, host, JobState.TOQUEUE, 1, Duration.SHORT, 1);

		sync();

		assertEquals(JobState.QUEUED, stateOf(fits));
		assertEquals(JobState.TOQUEUE, stateOf(tooMuch));
	}

	@Test
	void theWholeHostLimitCapsShortAndLongTogether() {
		host.setHostRateLimit(7);
		fx.persist(host);
		NarrowHttpJob shortJob = fx.narrowJob(routine, host, JobState.TOQUEUE, 4, Duration.SHORT, 3);
		NarrowHttpJob longJob = fx.narrowJob(routine, host, JobState.TOQUEUE, 6, Duration.LONG, 2);

		sync();

		// 4 + 6 = 10 > 7: the one of higher priority goes first, the other has to wait
		assertEquals(JobState.QUEUED, stateOf(shortJob));
		assertEquals(JobState.TOQUEUE, stateOf(longJob));
	}

	@Test
	void theProgramLimitCapsAllItsHostsTogether() {
		Host other = fx.host(program, "www.example.com");
		other.setHostRateLimit(10);
		other.setShortRateLimit(5);
		other.setLongRateLimit(5);
		fx.persist(other);
		NarrowHttpJob first = fx.narrowJob(routine, host, JobState.TOQUEUE, 4, Duration.SHORT, 2);
		NarrowHttpJob second = fx.narrowJob(routine, other, JobState.TOQUEUE, 5, Duration.SHORT, 1);
		NarrowHttpJob third = fx.narrowJob(routine, other, JobState.TOQUEUE, 5, Duration.LONG, 1);

		sync();

		// program limit 10: 4 + 5 = 9 fits, another 5 does not
		assertEquals(JobState.QUEUED, stateOf(first));
		assertEquals(JobState.QUEUED, stateOf(second));
		assertEquals(JobState.TOQUEUE, stateOf(third));
	}

	@Test
	void aJobOfAnotherProgramIsNotCountedAgainstTheProgramLimit() {
		Program otherProgram = fx.program("Other");
		otherProgram.setProgramRateLimit(10);
		fx.persist(otherProgram);
		Host otherHost = fx.host(otherProgram, "api.other.test");
		otherHost.setHostRateLimit(10);
		otherHost.setShortRateLimit(5);
		otherHost.setLongRateLimit(5);
		fx.persist(otherHost);
		fx.narrowJob(routine, otherHost, JobState.RUNNING, 5, Duration.SHORT, 1);
		NarrowHttpJob mine = fx.narrowJob(routine, host, JobState.TOQUEUE, 4, Duration.SHORT, 1);

		sync();

		assertEquals(JobState.QUEUED, stateOf(mine));
	}

	@Test
	void theHigherPriorityIsQueuedFirstWhenOnlyOneFits() {
		NarrowHttpJob low = fx.narrowJob(routine, host, JobState.TOQUEUE, 4, Duration.SHORT, 1);
		NarrowHttpJob high = fx.narrowJob(routine, host, JobState.TOQUEUE, 4, Duration.SHORT, 3);

		sync();

		assertEquals(JobState.QUEUED, stateOf(high));
		assertEquals(JobState.TOQUEUE, stateOf(low));
	}

	@Test
	void aPausedJobKeepsItsRateReserved() {
		fx.narrowJob(routine, host, JobState.PAUSED, 6, Duration.LONG, 1);
		NarrowHttpJob waiting = fx.narrowJob(routine, host, JobState.TOQUEUE, 1, Duration.LONG, 1);

		sync();

		assertEquals(JobState.TOQUEUE, stateOf(waiting));
	}

	@Test
	void anEndedJobFreesItsRate() {
		fx.narrowJob(routine, host, JobState.FINISHED, 6, Duration.LONG, 1);
		fx.narrowJob(routine, host, JobState.LOSTCONTACT, 6, Duration.LONG, 1);
		fx.narrowJob(routine, host, JobState.CANCELED, 6, Duration.LONG, 1);
		NarrowHttpJob waiting = fx.narrowJob(routine, host, JobState.TOQUEUE, 6, Duration.LONG, 1);

		sync();

		assertEquals(JobState.QUEUED, stateOf(waiting));
	}

	@Test
	void aJobQueuedByAFirstSyncCountsInTheSecond() {
		NarrowHttpJob first = fx.narrowJob(routine, host, JobState.TOQUEUE, 6, Duration.LONG, 1);
		sync();
		NarrowHttpJob second = fx.narrowJob(routine, host, JobState.TOQUEUE, 1, Duration.LONG, 1);

		sync();

		assertEquals(JobState.QUEUED, stateOf(first));
		assertEquals(JobState.TOQUEUE, stateOf(second));
	}

	// ---- wide jobs ----

	@Test
	void aWideJobIsQueuedWhenEveryHostCanTakeIt() {
		WideHttpJob wide = fx.wideJob(routine, program, JobState.TOQUEUE, 5, 1);

		sync();

		assertEquals(JobState.QUEUED, stateOf(wide));
		verify(queueService).queueHttpJob(any(), eq("fuzz"));
	}

	@Test
	void aWideJobWaitsWhenAHostWouldGoOverItsLimit() {
		fx.narrowJob(routine, host, JobState.RUNNING, 6, Duration.LONG, 1);
		WideHttpJob wide = fx.wideJob(routine, program, JobState.TOQUEUE, 5, 1);

		sync();

		// host limit 10: 6 narrow + 5 wide = 11
		assertEquals(JobState.TOQUEUE, stateOf(wide));
	}

	@Test
	void aWideJobCountsAgainstTheNarrowJobsQueuedAfterIt() {
		WideHttpJob wide = fx.wideJob(routine, program, JobState.TOQUEUE, 6, 3);
		NarrowHttpJob narrow = fx.narrowJob(routine, host, JobState.TOQUEUE, 5, Duration.SHORT, 1);

		sync();

		assertEquals(JobState.QUEUED, stateOf(wide));
		// 6 (wide) + 5 (narrow) = 11 > the host limit of 10
		assertEquals(JobState.TOQUEUE, stateOf(narrow));
	}

	@Test
	void aWideJobIsBlockedByTheLimitOfAnyHostOfTheProgram() {
		Host small = fx.host(program, "small.example.com");
		small.setHostRateLimit(2);
		small.setShortRateLimit(1);
		small.setLongRateLimit(1);
		fx.persist(small);
		WideHttpJob wide = fx.wideJob(routine, program, JobState.TOQUEUE, 3, 1);

		sync();

		assertEquals(JobState.TOQUEUE, stateOf(wide));
	}

	// ---- repository queries ----

	@Test
	void programRatesAreZeroForAProgramWithoutJobs() {
		var rates = jobRepo.getProgramsRateDtos(Set.of(program.getId()), Set.of(JobState.QUEUED, JobState.RUNNING, JobState.PAUSED));
		var wideRates = wideRepo.getProgramsWideRateDtos(Set.of(program.getId()), Set.of(JobState.QUEUED, JobState.RUNNING));

		assertEquals(1, rates.size());
		assertEquals(10, rates.get(0).rateLimit());
		assertEquals(0L, rates.get(0).currentRate());
		assertEquals(0L, wideRates.get(0).currentWideRate());
	}

	@Test
	void programRatesSumNarrowAndWideJobsAndWideRatesOnlyWide() {
		fx.narrowJob(routine, host, JobState.RUNNING, 2, Duration.SHORT, 1);
		fx.wideJob(routine, program, JobState.QUEUED, 3, 1);
		fx.wideJob(routine, program, JobState.FINISHED, 9, 1);
		fx.clear();

		var rates = jobRepo.getProgramsRateDtos(Set.of(program.getId()), Set.of(JobState.QUEUED, JobState.RUNNING, JobState.PAUSED));
		var wideRates = wideRepo.getProgramsWideRateDtos(Set.of(program.getId()), Set.of(JobState.QUEUED, JobState.RUNNING, JobState.PAUSED));

		assertEquals(5L, rates.get(0).currentRate());
		assertEquals(3L, wideRates.get(0).currentWideRate());
	}

	@Test
	void jobsInUseOfAHostAreFoundByTheirStates() {
		fx.narrowJob(routine, host, JobState.DRAFT, 1, Duration.SHORT, 1);
		fx.wideJob(routine, program, JobState.DRAFT, 1, 1);
		fx.clear();
		Set<JobState> inProgress = Set.of(JobState.QUEUED, JobState.RUNNING, JobState.PAUSED, JobState.TOQUEUE);

		assertEquals(false, narrowRepo.existsByHostIdAndCurrentStateIn(host.getId(), inProgress));
		assertEquals(false, wideRepo.existsByProgramIdAndCurrentStateIn(program.getId(), inProgress));

		fx.narrowJob(routine, host, JobState.TOQUEUE, 1, Duration.SHORT, 1);
		fx.wideJob(routine, program, JobState.RUNNING, 1, 1);
		fx.clear();

		assertTrue(narrowRepo.existsByHostIdAndCurrentStateIn(host.getId(), inProgress));
		assertTrue(wideRepo.existsByProgramIdAndCurrentStateIn(program.getId(), inProgress));
	}

	@Test
	void updateJobsStateOnlyMovesTheJobsStillInTheExpectedState() {
		NarrowHttpJob stillWaiting = fx.narrowJob(routine, host, JobState.TOQUEUE, 1, Duration.SHORT, 1);
		NarrowHttpJob canceledMeanwhile = fx.narrowJob(routine, host, JobState.CANCELED, 1, Duration.SHORT, 1);
		fx.clear();

		jobRepo.updateJobsState(Set.of(stillWaiting.getId(), canceledMeanwhile.getId()), JobState.QUEUED, JobState.TOQUEUE);

		assertEquals(JobState.QUEUED, stateOf(stillWaiting));
		assertEquals(JobState.CANCELED, stateOf(canceledMeanwhile));
	}

	@Test
	void updateJobsStateBumpsTheVersionSoAStaleWriteFails() {
		NarrowHttpJob job = fx.narrowJob(routine, host, JobState.TOQUEUE, 1, Duration.SHORT, 1);
		fx.clear();
		Long versionBefore = jobRepo.findById(job.getId()).orElseThrow().getVersion();
		fx.clear();

		jobRepo.updateJobsState(Set.of(job.getId()), JobState.QUEUED, JobState.TOQUEUE);
		fx.clear();

		assertEquals(versionBefore + 1, jobRepo.findById(job.getId()).orElseThrow().getVersion());
	}

	@Test
	void lostContactFindsOnlyHeldJobsWithAnOldContact() {
		NarrowHttpJob stale = fx.narrowJob(routine, host, JobState.RUNNING, 1, Duration.SHORT, 1);
		stale.getGenericDetails().setLastWorkerContactAt(Instant.now().minus(2, ChronoUnit.HOURS));
		fx.persist(stale);
		NarrowHttpJob fresh = fx.narrowJob(routine, host, JobState.RUNNING, 1, Duration.SHORT, 1);
		fresh.getGenericDetails().setLastWorkerContactAt(Instant.now());
		fx.persist(fresh);
		fx.narrowJob(routine, host, JobState.RUNNING, 1, Duration.SHORT, 1); // never heard of
		NarrowHttpJob finishedLongAgo = fx.narrowJob(routine, host, JobState.FINISHED, 1, Duration.SHORT, 1);
		finishedLongAgo.getGenericDetails().setLastWorkerContactAt(Instant.now().minus(5, ChronoUnit.HOURS));
		fx.persist(finishedLongAgo);
		fx.clear();

		int moved = stateService.markJobsWithLostContact(Instant.now(), HttpJobStateService.LOST_CONTACT_AFTER);
		fx.clear();

		assertEquals(1, moved);
		assertEquals(JobState.LOSTCONTACT, stateOf(stale));
		assertEquals(JobState.RUNNING, stateOf(fresh));
		assertEquals(JobState.FINISHED, stateOf(finishedLongAgo));
		assertTrue(jobRepo.findById(stale.getId()).orElseThrow().getResult().getFailureMsg().contains("30 minutes"));
	}

	@Test
	void aWorkerContactIsRecordedOnlyForAnExistingJob() {
		NarrowHttpJob job = fx.narrowJob(routine, host, JobState.RUNNING, 1, Duration.SHORT, 1);
		fx.clear();

		stateService.recordWorkerContact(job.getId());
		fx.clear();

		assertTrue(jobRepo.findById(job.getId()).orElseThrow().getGenericDetails().getLastWorkerContactAt() != null);
		org.junit.jupiter.api.Assertions.assertThrows(jakarta.persistence.EntityNotFoundException.class,
				() -> stateService.recordWorkerContact(-1L));
		assertEquals(List.of(), hostRepo.findAll().stream().filter(h -> h.getId() < 0).toList());
	}
}
