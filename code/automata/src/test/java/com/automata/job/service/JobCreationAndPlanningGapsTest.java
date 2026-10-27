package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.job.api.dto.GenericHttpJobDetailsDto;
import com.automata.job.api.dto.NarrowHttpJobDetailsDto;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.domain.valueobject.HttpJobInternalDto;
import com.automata.job.domain.valueobject.ProgramSummaryRatesDto;
import com.automata.job.domain.valueobject.TargetSelectionResult;
import com.automata.job.repository.HttpJobRepository;
import com.automata.job.repository.NarrowHttpJobRepository;
import com.automata.program.Program;
import com.automata.program.ProgramRepository;
import com.automata.request.RequestRepository;
import com.automata.routine.RoutineRepository;
import com.automata.routine.common.enums.Duration;
import com.automata.tenant.Tenant;
import com.automata.tenant.TenantRepository;
import com.automata.testsupport.KnownBug;
import com.automata.wordlist.WordlistRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Behaviour of the creation and of the queueing of jobs which is wrong today, see .claude/functionality-bugs.md. */
@ExtendWith(MockitoExtension.class)
class JobCreationAndPlanningGapsTest {

	private static final Long JOB_HOST_ID = 4L;

	@Mock
	HttpJobRepository jobRepo;
	@Mock
	RoutineRepository routineRepo;
	@Mock
	ProgramRepository programRepo;
	@Mock
	HostRepository hostRepo;
	@Mock
	RequestRepository requestRepo;
	@Mock
	TenantRepository tenantRepo;
	@Mock
	WordlistRepository wordlistRepo;
	@Mock
	TargetSelectionService targetSelectionService;
	@Mock
	JobTargetConfigService targetConfigService;
	@Mock
	JobConfigFileAsyncService configFileService;
	@Mock
	CustomConfigValidator customConfigValidator;
	@Mock
	com.automata.agentcontext.AgentContextService agentContextService;

	@InjectMocks
	HttpJobCreationService creationService;

	@Mock
	NarrowHttpJobRepository narrowJobRepo;

	@BeforeEach
	void startTransactionSynchronization() {
		TransactionSynchronizationManager.initSynchronization();
	}

	@AfterEach
	void clearTransactionSynchronization() {
		TransactionSynchronizationManager.clearSynchronization();
	}

	private void givenEverythingElseIsValid() {
		lenient().when(routineRepo.findById(1L)).thenReturn(Optional.of(RoutineFixtures.usableRoutine()));
		Host host = Host.of(JOB_HOST_ID);
		host.setHostRateLimit(10);
		Program program = new Program();
		program.setId(1L);
		host.setProgram(program);
		lenient().when(hostRepo.findById(JOB_HOST_ID)).thenReturn(Optional.of(host));
		lenient().when(jobRepo.save(any(HttpJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(targetSelectionService.handleTargetSelector(any()))
				.thenReturn(new TargetSelectionResult.SingleHost(JOB_HOST_ID));
		lenient().when(targetConfigService.configureHostAsTarget(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	// ---- the tenant of a job ----

	@Test
	@KnownBug("BUG-042")
	void aTenantOfAnotherHostIsRefusedForANarrowJob() {
		givenEverythingElseIsValid();
		Tenant foreign = Tenant.builder().id(9L).name("t").email("e").host(Host.of(99L)).build();
		when(tenantRepo.findById(9L)).thenReturn(Optional.of(foreign));
		GenericHttpJobDetailsDto generic = new GenericHttpJobDetailsDto(ResultsVerbosity.NORMAL, 2, 5, 1L, null,
				new TargetSelector(SelectorType.SINGLE_HOST, null), List.of(), null);

		// the authentication of a tenant of another host would be sent to this host
		assertThrows(IllegalArgumentException.class,
				() -> creationService.createDraftNarrowJob(generic, new NarrowHttpJobDetailsDto(Duration.SHORT, JOB_HOST_ID, 9L)));
	}

	@Test
	@KnownBug("BUG-016")
	void aJobWithoutATargetSelectorIsRefusedAsABadRequest() {
		TargetSelectionService real = new TargetSelectionService(hostRepo, requestRepo);

		// a job created without "targetSelector" ends in a NullPointerException (HTTP 500)
		assertThrows(IllegalArgumentException.class, () -> real.handleTargetSelector(null));
	}

	// ---- the targets which cannot be selected ----

	@Test
	@KnownBug("BUG-041")
	void aMultipleHostsSelectorCanBeHandledSoThatAWideJobCanBeCreated() throws Exception {
		TargetSelectionService real = new TargetSelectionService(hostRepo, requestRepo);
		when(hostRepo.existsById(any())).thenReturn(true);
		TargetSelector selector = new TargetSelector(SelectorType.MULTIPLE_HOSTS, new ObjectMapper().readTree("{\"hostsIds\":[1,2]}"));

		TargetSelectionResult result = real.handleTargetSelector(selector);

		assertTrue(result instanceof TargetSelectionResult.MultipleHosts);
	}

	@Test
	@KnownBug("BUG-041")
	void aMultipleRequestsSelectorCanBeHandled() throws Exception {
		TargetSelectionService real = new TargetSelectionService(hostRepo, requestRepo);
		TargetSelector selector = new TargetSelector(SelectorType.MULTIPLE_REQUESTS, new ObjectMapper().readTree("{\"requestsIds\":[1,2]}"));

		TargetSelectionResult result = real.handleTargetSelector(selector);

		assertTrue(result instanceof TargetSelectionResult.MultipleRequests);
	}

	// ---- a job that can never be queued ----

	private static HttpJobInternalDto narrow(long id, int rate, int priority, long createdAtSecond) {
		return new HttpJobInternalDto(id, Instant.ofEpochSecond(createdAtSecond), HttpJobScope.NARROW, Duration.SHORT, priority, rate, 1L, 10L);
	}

	@Test
	@KnownBug("BUG-043")
	void aJobAboveTheProgramLimitDoesNotBlockTheSmallJobsBehindIt() {
		JobDispatchPlanner planner = new JobDispatchPlanner(narrowJobRepo, hostRepo);
		HttpJobInternalDto oversized = narrow(1, 50, 3, 1);
		HttpJobInternalDto small = narrow(2, 2, 1, 2);
		when(hostRepo.findRateLimitsAllDtoById(10L))
				.thenReturn(new com.automata.host.common.dto.HostAllRateLimitsInternalDto(10L, 100, 50, 50));
		Map<Long, ProgramSummaryRatesDto> rates = Map.of(1L,
				ProgramSummaryRatesDto.builder().rateLimit(10).currentRate(0L).currentWideRate(0L).build());

		List<HttpJobInternalDto> accepted = planner.plan(List.of(oversized, small), rates);

		// the small job fits in the program limit of 10, only the oversized one never can
		assertEquals(List.of(small), accepted);
	}

	@Test
	@KnownBug("BUG-043")
	void aUserJobWithARateAboveTheLimitOfItsHostIsRefusedAtCreation() {
		givenEverythingElseIsValid();
		GenericHttpJobDetailsDto generic = new GenericHttpJobDetailsDto(ResultsVerbosity.NORMAL, 2, 500, 1L, null,
				new TargetSelector(SelectorType.SINGLE_HOST, null), List.of(), null);

		// the host accepts 10 requests per second: this job could never be queued
		assertThrows(IllegalArgumentException.class,
				() -> creationService.createDraftNarrowJob(generic, new NarrowHttpJobDetailsDto(Duration.SHORT, JOB_HOST_ID, null)));
	}
}
