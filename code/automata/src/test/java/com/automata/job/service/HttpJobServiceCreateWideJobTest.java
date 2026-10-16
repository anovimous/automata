package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.automata.host.HostRepository;
import com.automata.job.api.dto.GenericHttpJobDetailsDto;
import com.automata.job.api.dto.WideHttpJobDetailsDto;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.domain.valueobject.TargetSelectionResult;
import com.automata.job.infra.S3Service;
import com.automata.job.repository.HttpJobRepository;
import com.automata.program.Program;
import com.automata.program.ProgramRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.tenant.TenantRepository;
import com.automata.wordlist.WordlistRepository;

@ExtendWith(MockitoExtension.class)
class HttpJobServiceCreateWideJobTest {

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
	S3Service s3;

	@InjectMocks
	HttpJobService service;

	@BeforeEach
	void startTransactionSynchronization() {
		// what an active transaction does, since the service is called without one
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

	private void givenWideJobCanBeCreated() {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(new Routine()));
		when(programRepo.findById(2L)).thenReturn(Optional.of(new Program()));
		when(jobRepo.save(any(HttpJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(targetSelectionService.handleTargetSelector(any()))
				.thenReturn(new TargetSelectionResult.MultipleHosts(Set.of(5L)));
		when(targetConfigService.configureHostsAsTarget(any(), eq(Set.of(5L))))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void wideJobIsCreatedAsAWideDraft() {
		givenWideJobCanBeCreated();

		WideHttpJob job = service.createDraftWideJob(genericDto(SelectorType.MULTIPLE_HOSTS),
				new WideHttpJobDetailsDto(2L));

		ArgumentCaptor<HttpJob> saved = ArgumentCaptor.forClass(HttpJob.class);
		verify(jobRepo).save(saved.capture());
		assertSame(job, saved.getValue());
		assertEquals(HttpJobScope.WIDE, job.getGenericDetails().getHttpJobScope());
		assertEquals(JobState.DRAFT, job.getGenericDetails().getCurrentState());
	}

	@Test
	void wideConfigFileIsCreatedOnlyAfterTheTransactionCommits() {
		givenWideJobCanBeCreated();

		WideHttpJob job = service.createDraftWideJob(genericDto(SelectorType.MULTIPLE_HOSTS),
				new WideHttpJobDetailsDto(2L));

		verifyNoInteractions(configFileService);

		List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
		assertEquals(1, synchronizations.size());
		synchronizations.get(0).afterCommit();

		verify(configFileService).createWideConfigFile(job);
	}

	@Test
	void wideConfigFileIsNotCreatedWhenTheTransactionDoesNotCommit() {
		givenWideJobCanBeCreated();

		service.createDraftWideJob(genericDto(SelectorType.MULTIPLE_HOSTS), new WideHttpJobDetailsDto(2L));

		TransactionSynchronizationManager.getSynchronizations()
				.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

		verify(configFileService, never()).createWideConfigFile(any());
	}

	@Test
	void selectorWhichCanNotTargetAWideJobIsRejectedWithoutCreatingFiles() {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(new Routine()));
		when(programRepo.findById(2L)).thenReturn(Optional.of(new Program()));
		when(jobRepo.save(any(HttpJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(targetSelectionService.handleTargetSelector(any())).thenReturn(new TargetSelectionResult.SingleHost(5L));

		assertThrows(IllegalArgumentException.class, () -> service.createDraftWideJob(
				genericDto(SelectorType.SINGLE_HOST), new WideHttpJobDetailsDto(2L)));

		assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
		verifyNoInteractions(configFileService);
	}

}
