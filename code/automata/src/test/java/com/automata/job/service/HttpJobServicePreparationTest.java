package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.ConfigFileStatus;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.repository.HttpJobRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class HttpJobServicePreparationTest {

	@Mock
	HttpJobRepository jobRepo;
	@Mock
	JobConfigFileAsyncService configFileService;

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

	private HttpJob job(JobState state, ConfigFileStatus status) {
		HttpJob job = new WideHttpJob();
		job.setId(1L);
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(state).configFileStatus(status)
				.configFileFailure("old failure").configFileAttempts(3).build());
		when(jobRepo.findById(1L)).thenReturn(Optional.of(job));
		return job;
	}

	// ---- prepareJobForQueueing

	@Test
	void draftJobWithReadyFilesIsPreparedForQueueing() {
		HttpJob job = job(JobState.DRAFT, ConfigFileStatus.READY);

		service.prepareJobForQueueing(1L);

		assertEquals(JobState.TOQUEUE, job.getGenericDetails().getCurrentState());
		verify(jobRepo).save(job);
	}

	@ParameterizedTest
	@EnumSource(value = ConfigFileStatus.class, names = "READY", mode = EnumSource.Mode.EXCLUDE)
	void draftJobWithoutReadyFilesIsNotPreparedForQueueing(ConfigFileStatus status) {
		HttpJob job = job(JobState.DRAFT, status);

		assertThrows(InvalidJobStateException.class, () -> service.prepareJobForQueueing(1L));

		assertEquals(JobState.DRAFT, job.getGenericDetails().getCurrentState());
		verify(jobRepo, never()).save(job);
	}

	// ---- retryConfigFilePreparation

	@ParameterizedTest
	@EnumSource(value = ConfigFileStatus.class, names = { "PREPARING", "FAILED" })
	void filesAreMarkedPreparingAgainAndBuiltAfterTheCommit(ConfigFileStatus status) {
		HttpJob job = job(JobState.DRAFT, status);

		service.retryConfigFilePreparation(1L);

		assertEquals(ConfigFileStatus.PREPARING, job.getGenericDetails().getConfigFileStatus());
		assertNull(job.getGenericDetails().getConfigFileFailure());
		assertEquals(0, job.getGenericDetails().getConfigFileAttempts());
		verify(jobRepo).save(job);

		verifyNoInteractions(configFileService);
		List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
		assertEquals(1, synchronizations.size());
		synchronizations.get(0).afterCommit();
		verify(configFileService).prepareConfigFiles(1L);
	}

	@Test
	void readyFilesAreNotBuiltAgain() {
		HttpJob job = job(JobState.DRAFT, ConfigFileStatus.READY);

		assertThrows(InvalidJobStateException.class, () -> service.retryConfigFilePreparation(1L));

		assertEquals(ConfigFileStatus.READY, job.getGenericDetails().getConfigFileStatus());
		verify(jobRepo, never()).save(job);
		assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
	}

	@Test
	void filesOfAJobWhichIsNotADraftAreNotBuiltAgain() {
		HttpJob job = job(JobState.TOQUEUE, ConfigFileStatus.FAILED);

		assertThrows(InvalidJobStateException.class, () -> service.retryConfigFilePreparation(1L));

		verify(jobRepo, never()).save(job);
		verifyNoInteractions(configFileService);
	}

	@Test
	void retryOfAnUnknownJobIsReported() {
		when(jobRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.retryConfigFilePreparation(1L));
	}

}
