package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.infra.S3Service;

@ExtendWith(MockitoExtension.class)
class JobConfigFileAsyncServiceTest {

	@Mock
	S3Service s3Service;
	@Mock
	JobDetailsService jobDetailsService;
	@Mock
	JobDataTmpStorageService tmpStorageService;

	@InjectMocks
	JobConfigFileAsyncService service;

	private static NarrowHttpJob narrowJob() {
		NarrowHttpJob job = new NarrowHttpJob();
		job.setId(10L);
		return job;
	}

	private static WideHttpJob wideJob() {
		WideHttpJob job = new WideHttpJob();
		job.setId(20L);
		return job;
	}

	@Test
	void narrowTempFileIsDeletedAfterItWasUploaded() {
		NarrowHttpJob job = narrowJob();
		when(tmpStorageService.tempStoreNarrowJobTargetData(job)).thenReturn("/tmp/10");

		service.createNarrowConfigFile(job);

		InOrder inOrder = inOrder(s3Service, tmpStorageService);
		inOrder.verify(s3Service).storeJobDataFile("/tmp/10", 10L);
		inOrder.verify(tmpStorageService).deleteTempFile("/tmp/10");
	}

	@Test
	void narrowTempFileIsDeletedWhenTheUploadFails() {
		NarrowHttpJob job = narrowJob();
		when(tmpStorageService.tempStoreNarrowJobTargetData(job)).thenReturn("/tmp/10");
		RuntimeException failure = new RuntimeException("S3 is down");
		doThrow(failure).when(s3Service).storeJobDataFile("/tmp/10", 10L);

		RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.createNarrowConfigFile(job));

		assertSame(failure, thrown);
		verify(tmpStorageService).deleteTempFile("/tmp/10");
	}

	@Test
	void nothingIsUploadedWhenTheNarrowTempFileCanNotBeWritten() {
		NarrowHttpJob job = narrowJob();
		when(tmpStorageService.tempStoreNarrowJobTargetData(job)).thenThrow(new RuntimeException("write failed"));

		assertThrows(RuntimeException.class, () -> service.createNarrowConfigFile(job));

		verify(s3Service, never()).storeJobDataFile(org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void wideTempFileIsDeletedAfterItWasUploaded() {
		WideHttpJob job = wideJob();
		when(tmpStorageService.tempStoreWideJobTargetData(job)).thenReturn("/tmp/20");

		service.createWideConfigFile(job);

		InOrder inOrder = inOrder(s3Service, tmpStorageService);
		inOrder.verify(s3Service).storeJobDataFile("/tmp/20", 20L);
		inOrder.verify(tmpStorageService).deleteTempFile("/tmp/20");
	}

	@Test
	void wideTempFileIsDeletedWhenTheUploadFails() {
		WideHttpJob job = wideJob();
		when(tmpStorageService.tempStoreWideJobTargetData(job)).thenReturn("/tmp/20");
		doThrow(new RuntimeException("S3 is down")).when(s3Service).storeJobDataFile("/tmp/20", 20L);

		assertThrows(RuntimeException.class, () -> service.createWideConfigFile(job));

		verify(tmpStorageService).deleteTempFile("/tmp/20");
	}

}
