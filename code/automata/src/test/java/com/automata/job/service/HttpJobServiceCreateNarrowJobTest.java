package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
class HttpJobServiceCreateNarrowJobTest {

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
	S3Service s3;

	@InjectMocks
	HttpJobService service;

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
		when(routineRepo.findById(1L)).thenReturn(Optional.of(new Routine()));
		Host host = Host.of(JOB_HOST_ID);
		when(hostRepo.findById(JOB_HOST_ID)).thenReturn(Optional.of(host));
		when(jobRepo.save(any(HttpJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(targetSelectionService.handleTargetSelector(any())).thenReturn(selectionResult);
	}

	private static NarrowHttpJobDetailsDto narrowDto() {
		return new NarrowHttpJobDetailsDto(Duration.SHORT, JOB_HOST_ID, null);
	}

	@Test
	void singleRequestOfTheJobHostIsConfiguredAsTarget() {
		givenNarrowJobCanBeCreated(TargetSelectionResult.builder().requestId(9L).hostId(JOB_HOST_ID).build());
		when(targetConfigService.configureRequestsAsTarget(any(), any()))
				.thenAnswer(invocation -> invocation.getArgument(0));

		NarrowHttpJob job = service.createDraftNarrowJob(genericDto(SelectorType.SINGLE_REQUEST), narrowDto());

		org.mockito.Mockito.verify(targetConfigService).configureRequestsAsTarget(job, Set.of(9L));
		assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
	}

	@Test
	void singleHostOfTheJobHostIsConfiguredAsTarget() {
		givenNarrowJobCanBeCreated(TargetSelectionResult.builder().hostId(JOB_HOST_ID).build());
		when(targetConfigService.configureHostAsTarget(any(), any()))
				.thenAnswer(invocation -> invocation.getArgument(0));

		NarrowHttpJob job = service.createDraftNarrowJob(genericDto(SelectorType.SINGLE_HOST), narrowDto());

		org.mockito.Mockito.verify(targetConfigService).configureHostAsTarget(job, JOB_HOST_ID);
	}

	@Test
	void requestOfAnotherHostIsRejectedWithoutCreatingFiles() {
		givenNarrowJobCanBeCreated(TargetSelectionResult.builder().requestId(9L).hostId(99L).build());

		IneligibleJobTargetException ex = assertThrows(IneligibleJobTargetException.class,
				() -> service.createDraftNarrowJob(genericDto(SelectorType.SINGLE_REQUEST), narrowDto()));

		assertEquals("The target belongs to host 99, but the job is for host 4, so it can't be used as its target.",
				ex.getMessage());
		verifyNoInteractions(targetConfigService, configFileService);
		assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
	}

	@Test
	void hostOtherThanTheJobHostIsRejected() {
		givenNarrowJobCanBeCreated(TargetSelectionResult.builder().hostId(99L).build());

		assertThrows(IneligibleJobTargetException.class,
				() -> service.createDraftNarrowJob(genericDto(SelectorType.SINGLE_HOST), narrowDto()));

		verifyNoInteractions(targetConfigService, configFileService);
	}

}
