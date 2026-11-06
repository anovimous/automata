package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.enums.ConfigFileStatus;
import com.automata.job.domain.policy.JobPreparationPolicy;
import com.automata.job.domain.valueobject.JobDetailsFileContainer;
import com.automata.job.infra.S3Service;
import com.automata.job.repository.HttpJobRepository;

@ExtendWith(MockitoExtension.class)
class JobConfigFileAsyncServiceTest {

	@Mock
	S3Service s3Service;
	@Mock
	JobDetailsService jobDetailsService;
	@Mock
	JobDataTmpStorageService tmpStorageService;
	@Mock
	HttpJobRepository jobRepo;

	@InjectMocks
	JobConfigFileAsyncService service;

	private NarrowHttpJob narrowJob() {
		NarrowHttpJob job = new NarrowHttpJob();
		job.setId(10L);
		when(jobRepo.findById(10L)).thenReturn(Optional.of(job));
		return job;
	}

	private WideHttpJob wideJob() {
		WideHttpJob job = new WideHttpJob();
		job.setId(20L);
		when(jobRepo.findById(20L)).thenReturn(Optional.of(job));
		return job;
	}

	@Test
	void narrowFilesAreStoredTheTempFileIsDeletedAndTheJobIsReady() {
		NarrowHttpJob job = narrowJob();
		when(tmpStorageService.tempStoreNarrowJobTargetData(job)).thenReturn("/tmp/10");

		service.prepareConfigFiles(10L);

		InOrder inOrder = inOrder(s3Service, tmpStorageService, jobRepo);
		inOrder.verify(s3Service).storeJobDataFile("/tmp/10", 10L);
		inOrder.verify(tmpStorageService).deleteTempFile("/tmp/10");
		inOrder.verify(jobRepo).updateConfigFileStatus(10L, ConfigFileStatus.PREPARING, ConfigFileStatus.READY, null,
				1);
	}

	@Test
	void wideFilesAreStoredTheTempFileIsDeletedAndTheJobIsReady() {
		WideHttpJob job = wideJob();
		when(tmpStorageService.tempStoreWideJobTargetData(job)).thenReturn("/tmp/20");

		service.prepareConfigFiles(20L);

		InOrder inOrder = inOrder(s3Service, tmpStorageService, jobRepo);
		inOrder.verify(s3Service).storeJobDataFile("/tmp/20", 20L);
		inOrder.verify(tmpStorageService).deleteTempFile("/tmp/20");
		inOrder.verify(jobRepo).updateConfigFileStatus(20L, ConfigFileStatus.PREPARING, ConfigFileStatus.READY, null,
				1);
	}

	@Test
	void detailsObjectIsStoredBeforeTheDataFile() {
		NarrowHttpJob job = narrowJob();
		JobDetailsFileContainer container = JobDetailsFileContainer.builder().jobId(10L).build();
		when(jobDetailsService.createJobDetailsContainer(job)).thenReturn(container);
		when(tmpStorageService.tempStoreNarrowJobTargetData(job)).thenReturn("/tmp/10");

		service.prepareConfigFiles(10L);

		InOrder inOrder = inOrder(s3Service);
		inOrder.verify(s3Service).storeJobDetailsObject(container);
		inOrder.verify(s3Service).storeJobDataFile("/tmp/10", 10L);
	}

	@Test
	void tempFileIsDeletedWhenTheUploadFails() {
		NarrowHttpJob job = narrowJob();
		when(tmpStorageService.tempStoreNarrowJobTargetData(job)).thenReturn("/tmp/10");
		doThrow(new RuntimeException("S3 is down")).when(s3Service).storeJobDataFile("/tmp/10", 10L);

		service.prepareConfigFiles(10L);

		verify(tmpStorageService, times(JobConfigFileAsyncService.MAX_ATTEMPTS)).deleteTempFile("/tmp/10");
	}

	@Test
	void jobIsReadyAfterAFailureOnTheFirstTwoAttempts() {
		NarrowHttpJob job = narrowJob();
		when(tmpStorageService.tempStoreNarrowJobTargetData(job)).thenReturn("/tmp/10");
		doThrow(new RuntimeException("S3 is down")).doThrow(new RuntimeException("S3 is down")).doNothing()
				.when(s3Service).storeJobDataFile("/tmp/10", 10L);

		service.prepareConfigFiles(10L);

		verify(jobRepo).updateConfigFileStatus(10L, ConfigFileStatus.PREPARING, ConfigFileStatus.READY, null, 3);
		verify(s3Service, never()).deleteJobDetailsObject(anyLong());
		verify(s3Service, never()).deleteJobDataFile(anyLong());
	}

	@Test
	void jobFailsAfterTheLastAttemptAndTheStoredFilesAreRemoved() {
		NarrowHttpJob job = narrowJob();
		when(tmpStorageService.tempStoreNarrowJobTargetData(job)).thenReturn("/tmp/10");
		doThrow(new IllegalStateException("S3 is down")).when(s3Service).storeJobDataFile("/tmp/10", 10L);

		service.prepareConfigFiles(10L);

		verify(s3Service, times(JobConfigFileAsyncService.MAX_ATTEMPTS)).storeJobDataFile("/tmp/10", 10L);
		InOrder inOrder = inOrder(s3Service, jobRepo);
		inOrder.verify(s3Service).deleteJobDetailsObject(10L);
		inOrder.verify(s3Service).deleteJobDataFile(10L);
		inOrder.verify(jobRepo).updateConfigFileStatus(10L, ConfigFileStatus.PREPARING, ConfigFileStatus.FAILED,
				"IllegalStateException: S3 is down", JobConfigFileAsyncService.MAX_ATTEMPTS);
	}

	@Test
	void failureOfTheTempFileWriteFailsTheJobWithoutUploadingTheDataFile() {
		NarrowHttpJob job = narrowJob();
		when(tmpStorageService.tempStoreNarrowJobTargetData(job)).thenThrow(new RuntimeException("write failed"));

		service.prepareConfigFiles(10L);

		verify(s3Service, never()).storeJobDataFile(any(), any());
		verify(jobRepo).updateConfigFileStatus(10L, ConfigFileStatus.PREPARING, ConfigFileStatus.FAILED,
				"RuntimeException: write failed", JobConfigFileAsyncService.MAX_ATTEMPTS);
	}

	@Test
	void failureWithoutAMessageIsDescribedByItsType() {
		NarrowHttpJob job = narrowJob();
		when(tmpStorageService.tempStoreNarrowJobTargetData(job)).thenThrow(new IllegalStateException());

		service.prepareConfigFiles(10L);

		verify(jobRepo).updateConfigFileStatus(10L, ConfigFileStatus.PREPARING, ConfigFileStatus.FAILED,
				"IllegalStateException: no message", JobConfigFileAsyncService.MAX_ATTEMPTS);
	}

	@Test
	void longFailureMessageIsCut() {
		NarrowHttpJob job = narrowJob();
		when(tmpStorageService.tempStoreNarrowJobTargetData(job)).thenThrow(new RuntimeException("x".repeat(500)));

		service.prepareConfigFiles(10L);

		ArgumentCaptor<String> failure = ArgumentCaptor.forClass(String.class);
		verify(jobRepo).updateConfigFileStatus(eq(10L), eq(ConfigFileStatus.PREPARING), eq(ConfigFileStatus.FAILED),
				failure.capture(), anyInt());
		assertEquals(JobPreparationPolicy.MAX_FAILURE_LENGTH, failure.getValue().length());
	}

	@Test
	void jobStillFailsWhenTheStoredFilesCanNotBeRemoved() {
		NarrowHttpJob job = narrowJob();
		when(tmpStorageService.tempStoreNarrowJobTargetData(job)).thenThrow(new RuntimeException("write failed"));
		doThrow(new RuntimeException("S3 is down")).when(s3Service).deleteJobDetailsObject(10L);
		doThrow(new RuntimeException("S3 is down")).when(s3Service).deleteJobDataFile(10L);

		service.prepareConfigFiles(10L);

		verify(jobRepo).updateConfigFileStatus(eq(10L), eq(ConfigFileStatus.PREPARING), eq(ConfigFileStatus.FAILED),
				any(), anyInt());
	}

	@Test
	void failureOfTheDetailsContainerFailsTheJob() {
		NarrowHttpJob job = narrowJob();
		when(jobDetailsService.createJobDetailsContainer(job)).thenThrow(new RuntimeException("no routine"));

		service.prepareConfigFiles(10L);

		verifyNoInteractions(tmpStorageService);
		verify(jobRepo).updateConfigFileStatus(10L, ConfigFileStatus.PREPARING, ConfigFileStatus.FAILED,
				"RuntimeException: no routine", JobConfigFileAsyncService.MAX_ATTEMPTS);
	}

	@Test
	void nothingIsDoneForAJobWhichDoesNotExist() {
		when(jobRepo.findById(30L)).thenReturn(Optional.empty());

		service.prepareConfigFiles(30L);

		verifyNoInteractions(s3Service, jobDetailsService, tmpStorageService);
		verify(jobRepo, never()).updateConfigFileStatus(anyLong(), any(), any(), isNull(), anyInt());
	}

}
