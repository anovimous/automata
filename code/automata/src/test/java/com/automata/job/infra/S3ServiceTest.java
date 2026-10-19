package com.automata.job.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.awspring.cloud.s3.S3Template;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

	@TempDir
	Path tmpDir;

	@Mock
	S3Template s3Template;

	@InjectMocks
	S3Service service;

	@Test
	void jobDataFileIsUploadedUnderTheJobIdAndTheFileIsClosedAfterwards() throws Exception {
		Path file = Files.writeString(tmpDir.resolve("5"), "line\n");

		service.storeJobDataFile(file.toString(), 5L);

		ArgumentCaptor<InputStream> uploaded = ArgumentCaptor.forClass(InputStream.class);
		verify(s3Template).upload(eq(S3Service.JOB_CONFIG_BUCKET), eq("data/5.jsonl"), uploaded.capture());
		assertThrows(IOException.class, () -> uploaded.getValue().read());
	}

	@Test
	void jobDataFileIsClosedEvenWhenTheUploadFails() throws Exception {
		Path file = Files.writeString(tmpDir.resolve("6"), "line\n");
		ArgumentCaptor<InputStream> uploaded = ArgumentCaptor.forClass(InputStream.class);
		when(s3Template.upload(any(), any(), uploaded.capture())).thenThrow(new IllegalStateException("S3 is down"));

		assertThrows(IllegalStateException.class, () -> service.storeJobDataFile(file.toString(), 6L));

		assertThrows(IOException.class, () -> uploaded.getValue().read());
	}

	@Test
	void missingJobDataFileIsReportedAndNothingIsUploaded() {
		RuntimeException e = assertThrows(RuntimeException.class,
				() -> service.storeJobDataFile(tmpDir.resolve("missing").toString(), 7L));

		assertEquals("Temporary file could not be accessed/found", e.getMessage());
		verifyNoInteractions(s3Template);
	}

	@Test
	void jobDetailsObjectIsDeletedUnderTheJobId() {
		service.deleteJobDetailsObject(8L);

		verify(s3Template).deleteObject(S3Service.JOB_CONFIG_BUCKET, "details/8.json");
	}

	@Test
	void jobDataFileIsDeletedUnderTheJobId() {
		service.deleteJobDataFile(9L);

		verify(s3Template).deleteObject(S3Service.JOB_CONFIG_BUCKET, "data/9.jsonl");
	}

}
