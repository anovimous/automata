package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.domain.valueobject.TargetSelectionResult;
import com.automata.job.infra.S3Service;
import com.automata.job.repository.HttpJobRepository;
import com.automata.program.ProgramRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.routine.common.enums.Duration;
import com.automata.tenant.TenantRepository;
import com.automata.wordlist.WordlistRepository;

@ExtendWith(MockitoExtension.class)
class HttpJobCreationServiceCreateNarrowJobTest {

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
	@Mock
	S3Service s3;

	@InjectMocks
	HttpJobCreationService service;

	@BeforeEach
	void startTransactionSynchronization() {
		TransactionSynchronizationManager.initSynchronization();
	}

	@AfterEach
	void clearTransactionSynchronization() {
		TransactionSynchronizationManager.clearSynchronization();
	}

	private static GenericHttpJobDetailsDto genericDto(SelectorType selectorType) {
		return new GenericHttpJobDetailsDto(ResultsVerbosity.NORMAL, 2, 10, 1L, null,
				new TargetSelector(selectorType, null), List.of(), null);
	}

	private void givenNarrowJobCanBeCreated(TargetSelectionResult selectionResult) {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(RoutineFixtures.usableRoutine()));
		Host host = Host.of(JOB_HOST_ID);
		host.setHostRateLimit(10);
		host.setShortRateLimit(10);
		host.setLongRateLimit(10);
		host.setProgram(programWithLimit(10));
		when(hostRepo.findById(JOB_HOST_ID)).thenReturn(Optional.of(host));
		when(jobRepo.save(any(HttpJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(targetSelectionService.handleTargetSelector(any())).thenReturn(selectionResult);
	}

	private static com.automata.program.Program programWithLimit(int limit) {
		com.automata.program.Program program = new com.automata.program.Program();
		program.setProgramRateLimit(limit);
		return program;
	}

	private static NarrowHttpJobDetailsDto narrowDto() {
		return new NarrowHttpJobDetailsDto(Duration.SHORT, JOB_HOST_ID, null);
	}

	@Test
	void singleRequestOfTheJobHostIsConfiguredAsTarget() {
		givenNarrowJobCanBeCreated(new TargetSelectionResult.SingleRequest(9L, JOB_HOST_ID));
		when(targetConfigService.configureRequestsAsTarget(any(), any()))
				.thenAnswer(invocation -> invocation.getArgument(0));

		NarrowHttpJob job = service.createDraftNarrowJob(genericDto(SelectorType.SINGLE_REQUEST), narrowDto());

		org.mockito.Mockito.verify(targetConfigService).configureRequestsAsTarget(job, Set.of(9L));
		assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
	}

	@Test
	void singleHostOfTheJobHostIsConfiguredAsTarget() {
		givenNarrowJobCanBeCreated(new TargetSelectionResult.SingleHost(JOB_HOST_ID));
		when(targetConfigService.configureHostAsTarget(any(), any()))
				.thenAnswer(invocation -> invocation.getArgument(0));

		NarrowHttpJob job = service.createDraftNarrowJob(genericDto(SelectorType.SINGLE_HOST), narrowDto());

		org.mockito.Mockito.verify(targetConfigService).configureHostAsTarget(job, JOB_HOST_ID);
	}

	@Test
	void multipleRequestsAreConfiguredAsTarget() {
		givenNarrowJobCanBeCreated(new TargetSelectionResult.MultipleRequests(Set.of(8L, 9L), Set.of(JOB_HOST_ID)));
		when(targetConfigService.configureRequestsAsTarget(any(), any()))
				.thenAnswer(invocation -> invocation.getArgument(0));

		NarrowHttpJob job = service.createDraftNarrowJob(genericDto(SelectorType.MULTIPLE_REQUESTS), narrowDto());

		org.mockito.Mockito.verify(targetConfigService).configureRequestsAsTarget(job, Set.of(8L, 9L));
	}

	@Test
	void multipleRequestsOfAnotherHostAreRefusedWithoutConfiguringTargets() {
		givenNarrowJobCanBeCreated(new TargetSelectionResult.MultipleRequests(Set.of(8L, 9L), Set.of(JOB_HOST_ID, 99L)));

		assertThrows(com.automata.job.domain.exception.IneligibleJobTargetException.class,
				() -> service.createDraftNarrowJob(genericDto(SelectorType.MULTIPLE_REQUESTS), narrowDto()));

		org.mockito.Mockito.verifyNoInteractions(targetConfigService);
		assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
	}

	@Test
	void hostsSelectionCanNotTargetANarrowJob() {
		givenNarrowJobCanBeCreated(new TargetSelectionResult.MultipleHosts(Set.of(5L), Set.of(1L)));

		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftNarrowJob(genericDto(SelectorType.MULTIPLE_HOSTS), narrowDto()));

		verifyNoInteractions(targetConfigService, configFileService);
		assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
	}

	@Test
	void requestOfAnotherHostIsRejectedWithoutCreatingFiles() {
		givenNarrowJobCanBeCreated(new TargetSelectionResult.SingleRequest(9L, 99L));

		IneligibleJobTargetException ex = assertThrows(IneligibleJobTargetException.class,
				() -> service.createDraftNarrowJob(genericDto(SelectorType.SINGLE_REQUEST), narrowDto()));

		assertEquals("The target belongs to host 99, but the job is for host 4, so it can't be used as its target.",
				ex.getMessage());
		verifyNoInteractions(targetConfigService, configFileService);
		assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
	}

	@Test
	void hostOtherThanTheJobHostIsRejected() {
		givenNarrowJobCanBeCreated(new TargetSelectionResult.SingleHost(99L));

		assertThrows(IneligibleJobTargetException.class,
				() -> service.createDraftNarrowJob(genericDto(SelectorType.SINGLE_HOST), narrowDto()));

		verifyNoInteractions(targetConfigService, configFileService);
	}

	private static GenericHttpJobDetailsDto genericDtoWithRate(int rate) {
		return new GenericHttpJobDetailsDto(ResultsVerbosity.NORMAL, 2, rate, 1L, null,
				new TargetSelector(SelectorType.SINGLE_HOST, null), List.of(), null);
	}

	private Host hostWithLimits(int hostLimit, int shortLimit, int longLimit, int programLimit) {
		Host host = Host.of(JOB_HOST_ID);
		host.setHostRateLimit(hostLimit);
		host.setShortRateLimit(shortLimit);
		host.setLongRateLimit(longLimit);
		host.setProgram(programWithLimit(programLimit));
		return host;
	}

	private void assertRefused(Host host, int rate, Duration duration, String limitName) {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(RoutineFixtures.usableRoutine()));
		when(hostRepo.findById(JOB_HOST_ID)).thenReturn(Optional.of(host));

		com.automata.job.domain.exception.IneligibleJobTargetException e = assertThrows(
				com.automata.job.domain.exception.IneligibleJobTargetException.class,
				() -> service.createDraftNarrowJob(genericDtoWithRate(rate),
						new NarrowHttpJobDetailsDto(duration, JOB_HOST_ID, null)));

		assertTrue(e.getMessage().contains(limitName));
		org.mockito.Mockito.verify(jobRepo, never()).save(any());
	}

	@Test
	void aRateAboveTheHostRateLimitIsRefusedBeforeAnythingIsSaved() {
		assertRefused(hostWithLimits(10, 100, 100, 100), 11, Duration.SHORT, "rate limit of the host");
	}

	@Test
	void aRateAboveTheProgramRateLimitIsRefused() {
		assertRefused(hostWithLimits(100, 100, 100, 10), 11, Duration.SHORT, "rate limit of the program");
	}

	@Test
	void aRateAboveTheShortRateLimitIsRefusedForAShortJob() {
		assertRefused(hostWithLimits(100, 5, 100, 100), 6, Duration.SHORT, "short rate limit");
	}

	@Test
	void aRateAboveTheLongRateLimitIsRefusedForALongJob() {
		assertRefused(hostWithLimits(100, 100, 5, 100), 6, Duration.LONG, "long rate limit");
	}

	@Test
	void aRateAboveTheLongRateLimitIsAcceptedForAShortJob() {
		givenNarrowJobCanBeCreated(new TargetSelectionResult.SingleHost(JOB_HOST_ID));
		when(hostRepo.findById(JOB_HOST_ID)).thenReturn(Optional.of(hostWithLimits(100, 100, 5, 100)));
		when(targetConfigService.configureHostAsTarget(any(), any()))
				.thenAnswer(invocation -> invocation.getArgument(0));

		assertEquals(6, service.createDraftNarrowJob(genericDtoWithRate(6), narrowDto()).getGenericDetails().getRate());
	}

	@Test
	void aRateAtTheLimitsIsAccepted() {
		givenNarrowJobCanBeCreated(new TargetSelectionResult.SingleHost(JOB_HOST_ID));
		when(targetConfigService.configureHostAsTarget(any(), any()))
				.thenAnswer(invocation -> invocation.getArgument(0));

		assertEquals(10, service.createDraftNarrowJob(genericDtoWithRate(10), narrowDto()).getGenericDetails().getRate());
	}

}
