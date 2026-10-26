package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
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
import com.automata.job.api.dto.WideHttpJobDetailsDto;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.infra.S3Service;
import com.automata.job.repository.HttpJobRepository;
import com.automata.program.Program;
import com.automata.program.ProgramRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.routine.common.enums.Duration;
import com.automata.routine.common.enums.TargetCardinalityPair;
import com.automata.tenant.TenantRepository;
import com.automata.wordlist.Wordlist;
import com.automata.wordlist.WordlistRepository;

import jakarta.persistence.EntityNotFoundException;

// The rules every job creation path applies to the rate, the priority, the wordlists and the routine
@ExtendWith(MockitoExtension.class)
class HttpJobCreationServiceRulesTest {

	@Mock
	HttpJobRepository jobRepo;
	@Mock
	RoutineRepository routineRepo;
	@Mock
	ProgramRepository programRepo;
	@Mock
	HostRepository hostRepo;
	@Mock
	com.automata.request.RequestRepository requestRepo;
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

	private Wordlist wordlist(Long id) {
		Wordlist wordlist = new Wordlist();
		wordlist.setId(id);
		return wordlist;
	}

	private GenericHttpJobDetailsDto generic(SelectorType selectorType, Integer rate, Integer priority,
			Set<Long> wordlistsIds) {
		return new GenericHttpJobDetailsDto(ResultsVerbosity.NORMAL, priority, rate, 1L, wordlistsIds,
				new TargetSelector(selectorType, null), List.of(), null);
	}

	private NarrowHttpJobDetailsDto narrowDto() {
		return new NarrowHttpJobDetailsDto(Duration.SHORT, 4L, null);
	}

	private Routine routineAllowing(TargetCardinalityPair... targets) {
		Routine routine = RoutineFixtures.usableRoutine();
		routine.setKey("fuzz");
		routine.setAllowedTargets(EnumSet.copyOf(List.of(targets)));
		return routine;
	}

	// ---- requireRateAndPriority

	@Test
	void missingRateAndPriorityPass() {
		service.requireRateAndPriority(null, null);
	}

	@Test
	void rateFromOneUpPasses() {
		service.requireRateAndPriority(1, null);
		service.requireRateAndPriority(500, null);
	}

	@Test
	void rateBelowOneIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> service.requireRateAndPriority(0, null));
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.requireRateAndPriority(-5, null));
		assertTrue(e.getMessage().contains("rate"));
	}

	@Test
	void priorityInTheRangePasses() {
		service.requireRateAndPriority(null, HttpJob.MIN_PRIORITY);
		service.requireRateAndPriority(null, HttpJob.MAX_PRIORITY);
	}

	@Test
	void priorityOutsideTheRangeIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> service.requireRateAndPriority(null, HttpJob.MIN_PRIORITY - 1));
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.requireRateAndPriority(null, HttpJob.MAX_PRIORITY + 1));
		assertTrue(e.getMessage().contains("priority"));
	}

	@Test
	void requirePriorityChecksTheBounds() {
		service.requirePriority(3);
		assertThrows(IllegalArgumentException.class, () -> service.requirePriority(0));
		assertThrows(IllegalArgumentException.class, () -> service.requirePriority(6));
	}

	@Test
	void aUserJobWithoutARateIsRejected() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.requireUserRateAndPriority(null, 2));
		assertEquals("rate is required", e.getMessage());
	}

	@Test
	void aUserJobWithAValidRateAndNoPriorityPasses() {
		service.requireUserRateAndPriority(1, null);
	}

	@Test
	void aUserJobWithARateOfZeroIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> service.requireUserRateAndPriority(0, 2));
	}

	@Test
	void narrowJobWithoutARateIsRejectedBeforeAnythingIsSaved() {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(RoutineFixtures.usableRoutine()));

		assertThrows(IllegalArgumentException.class, () -> service
				.createDraftNarrowJob(generic(SelectorType.SINGLE_HOST, null, 2, null), narrowDto()));

		verify(jobRepo, never()).save(any());
	}

	@Test
	void wideJobWithoutARateIsRejectedBeforeAnythingIsSaved() {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(RoutineFixtures.usableRoutine()));

		assertThrows(IllegalArgumentException.class, () -> service.createDraftWideJob(
				generic(SelectorType.MULTIPLE_HOSTS, null, 2, null), new WideHttpJobDetailsDto(2L)));

		verify(jobRepo, never()).save(any());
	}

	// ---- findWordlists

	@Test
	void noWordlistIdsGiveAnEmptyListWithoutQuerying() {
		assertEquals(List.of(), service.findWordlists((Set<Long>) null));
		assertEquals(List.of(), service.findWordlists(Set.of()));

		verifyNoInteractions(wordlistRepo);
	}

	@Test
	void existingWordlistsAreReturned() {
		when(wordlistRepo.findAllById(Set.of(3L, 4L))).thenReturn(List.of(wordlist(3L), wordlist(4L)));

		assertEquals(2, service.findWordlists(Set.of(3L, 4L)).size());
	}

	@Test
	void unknownWordlistIdsAreNotFoundAndListed() {
		when(wordlistRepo.findAllById(Set.of(3L, 8L, 9L))).thenReturn(List.of(wordlist(3L)));

		EntityNotFoundException e = assertThrows(EntityNotFoundException.class,
				() -> service.findWordlists(Set.of(3L, 8L, 9L)));

		assertEquals("Wordlist not found: [8, 9]", e.getMessage());
	}

	// ---- requireRoutineUsable

	@Test
	void routineNotAvailableAtTheWorkerIsRejected() {
		Routine routine = RoutineFixtures.usableRoutine();
		routine.setKey("fuzz");
		routine.setAvailableAtConsumer(false);

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.requireRoutineUsable(routine, SelectorType.SINGLE_REQUEST));

		assertTrue(e.getMessage().contains("not available"));
	}

	@Test
	void everySelectorTypeIsCheckedAgainstItsOwnTarget() {
		for (SelectorType type : SelectorType.values()) {
			TargetCardinalityPair expected = switch (type) {
			case SINGLE_HOST -> TargetCardinalityPair.SINGLE_HOST;
			case MULTIPLE_HOSTS -> TargetCardinalityPair.MULTILPLE_HOSTS;
			case SINGLE_REQUEST -> TargetCardinalityPair.SINGLE_REQUEST;
			case MULTIPLE_REQUESTS -> TargetCardinalityPair.MULTIPLE_REQUESTS;
			};

			service.requireRoutineUsable(routineAllowing(expected), type);

			for (TargetCardinalityPair other : TargetCardinalityPair.values())
				if (other != expected)
					assertThrows(IllegalArgumentException.class,
							() -> service.requireRoutineUsable(routineAllowing(other), type));
		}
	}

	@Test
	void routineWithoutAllowedTargetsAllowsNothing() {
		Routine routine = RoutineFixtures.usableRoutine();
		routine.setAllowedTargets(null);

		assertThrows(IllegalArgumentException.class,
				() -> service.requireRoutineUsable(routine, SelectorType.SINGLE_REQUEST));
	}

	@Test
	void withoutASelectorTypeOnlyTheAvailabilityIsChecked() {
		Routine routine = RoutineFixtures.usableRoutine();
		routine.setAllowedTargets(Set.of());

		service.requireRoutineUsable(routine, null);
	}

	// ---- narrow jobs

	@Test
	void narrowJobWithARateBelowOneIsRejectedBeforeAnythingIsSaved() {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(RoutineFixtures.usableRoutine()));

		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftNarrowJob(generic(SelectorType.SINGLE_HOST, 0, 2, null), narrowDto()));

		verify(jobRepo, never()).save(any());
	}

	@Test
	void narrowJobWithAPriorityOutsideTheRangeIsRejectedBeforeAnythingIsSaved() {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(RoutineFixtures.usableRoutine()));

		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftNarrowJob(generic(SelectorType.SINGLE_HOST, 10, 9, null), narrowDto()));

		verify(jobRepo, never()).save(any());
	}

	@Test
	void narrowJobWithARoutineThatDoesNotAllowTheTargetIsRejected() {
		when(routineRepo.findById(1L))
				.thenReturn(Optional.of(routineAllowing(TargetCardinalityPair.SINGLE_REQUEST)));

		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftNarrowJob(generic(SelectorType.SINGLE_HOST, 10, 2, null), narrowDto()));

		verify(jobRepo, never()).save(any());
	}

	@Test
	void narrowJobWithARoutineNotAvailableAtTheWorkerIsRejected() {
		Routine routine = RoutineFixtures.usableRoutine();
		routine.setAvailableAtConsumer(false);
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));

		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftNarrowJob(generic(SelectorType.SINGLE_HOST, 10, 2, null), narrowDto()));

		verify(jobRepo, never()).save(any());
	}

	@Test
	void narrowJobWithAnUnknownWordlistIsNotFoundAndNotSaved() {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(RoutineFixtures.usableRoutine()));
		when(hostRepo.findById(4L)).thenReturn(Optional.of(hostWithProgram()));
		when(wordlistRepo.findAllById(Set.of(3L))).thenReturn(List.of());

		assertThrows(EntityNotFoundException.class, () -> service
				.createDraftNarrowJob(generic(SelectorType.SINGLE_HOST, 10, 2, Set.of(3L)), narrowDto()));

		verify(jobRepo, never()).save(any());
	}

	private Host hostWithProgram() {
		Host host = Host.of(4L);
		host.setHostRateLimit(10);
		host.setProgram(new Program());
		return host;
	}

	@Test
	void narrowJobOnAHostWithARateLimitOfZeroIsRefusedBeforeAnythingIsSaved() {
		Host closed = Host.of(4L);
		closed.setHostRateLimit(0);
		when(routineRepo.findById(1L)).thenReturn(Optional.of(RoutineFixtures.usableRoutine()));
		when(hostRepo.findById(4L)).thenReturn(Optional.of(closed));

		assertThrows(com.automata.job.domain.exception.IneligibleJobTargetException.class, () -> service
				.createDraftNarrowJob(generic(SelectorType.SINGLE_HOST, 10, 2, null), narrowDto()));

		verify(jobRepo, never()).save(any());
	}

	// ---- wide jobs

	@Test
	void wideJobWithARateBelowOneIsRejectedBeforeAnythingIsSaved() {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(RoutineFixtures.usableRoutine()));

		assertThrows(IllegalArgumentException.class, () -> service
				.createDraftWideJob(generic(SelectorType.MULTIPLE_HOSTS, -1, 2, null), new WideHttpJobDetailsDto(2L)));

		verify(jobRepo, never()).save(any());
	}

	@Test
	void wideJobWithARoutineThatDoesNotAllowMultipleHostsIsRejected() {
		when(routineRepo.findById(1L))
				.thenReturn(Optional.of(routineAllowing(TargetCardinalityPair.SINGLE_REQUEST)));

		assertThrows(IllegalArgumentException.class, () -> service.createDraftWideJob(
				generic(SelectorType.MULTIPLE_HOSTS, 10, 2, null), new WideHttpJobDetailsDto(2L)));

		verify(jobRepo, never()).save(any());
	}

	@Test
	void wideJobTargetingAHostWithARateLimitOfZeroIsRefused() {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(RoutineFixtures.usableRoutine()));
		when(programRepo.findById(2L)).thenReturn(Optional.of(new Program()));
		when(jobRepo.save(any(com.automata.job.domain.model.HttpJob.class))).thenAnswer(inv -> inv.getArgument(0));
		when(targetSelectionService.handleTargetSelector(any()))
				.thenReturn(new com.automata.job.domain.valueobject.TargetSelectionResult.MultipleHosts(Set.of(5L, 6L)));
		when(hostRepo.findDtosByIds(Set.of(5L, 6L))).thenReturn(List.of(
				new com.automata.host.common.dto.HostRateLimitInternalDto(5L, 10),
				new com.automata.host.common.dto.HostRateLimitInternalDto(6L, 0)));

		com.automata.job.domain.exception.IneligibleJobTargetException e = assertThrows(
				com.automata.job.domain.exception.IneligibleJobTargetException.class,
				() -> service.createDraftWideJob(generic(SelectorType.MULTIPLE_HOSTS, 10, 2, null),
						new WideHttpJobDetailsDto(2L)));

		assertTrue(e.getMessage().contains("Host 6"));
		verify(targetConfigService, never()).configureHostsAsTarget(any(), any());
	}

	@Test
	void hostsAllWithARateLimitAreAccepted() {
		when(hostRepo.findDtosByIds(Set.of(5L))).thenReturn(List.of(
				new com.automata.host.common.dto.HostRateLimitInternalDto(5L, 1)));

		service.requireHostsAcceptJobs(Set.of(5L));
	}

	@Test
	void wideJobWithAnUnknownWordlistIsNotFoundAndNotSaved() {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(RoutineFixtures.usableRoutine()));
		when(programRepo.findById(2L)).thenReturn(Optional.of(new Program()));
		when(wordlistRepo.findAllById(Set.of(3L))).thenReturn(List.of());

		assertThrows(EntityNotFoundException.class, () -> service.createDraftWideJob(
				generic(SelectorType.MULTIPLE_HOSTS, 10, 2, Set.of(3L)), new WideHttpJobDetailsDto(2L)));

		verify(jobRepo, never()).save(any());
	}

}
