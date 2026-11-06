package com.automata.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import jakarta.persistence.EntityNotFoundException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class BodyObjectStorageTest {

	private S3Template s3 = mock(S3Template.class);

	private BodyObjectStorage storage = new BodyObjectStorage(s3, "req-bucket", "res-bucket");

	@BeforeEach
	void startTransactionSynchronization() {
		TransactionSynchronizationManager.initSynchronization();
	}

	@AfterEach
	void clearTransactionSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive())
			TransactionSynchronizationManager.clearSynchronization();
	}

	private void finishTransaction(int status) {
		TransactionSynchronizationUtils.triggerAfterCompletion(status);
		TransactionSynchronizationManager.clearSynchronization();
	}

	// store

	@Test
	void storeRequestBody_uploadsToTheRequestBucket_keyedById() throws Exception {
		storage.storeRequestBody(7L, new byte[] { 1, 2, 3 }, "multipart/form-data; boundary=x");

		ArgumentCaptor<InputStream> stream = ArgumentCaptor.forClass(InputStream.class);
		ArgumentCaptor<ObjectMetadata> metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
		verify(s3).upload(eq("req-bucket"), eq("7"), stream.capture(), metadata.capture());

		assertArrayEquals(new byte[] { 1, 2, 3 }, stream.getValue().readAllBytes());
		assertEquals("multipart/form-data; boundary=x", metadata.getValue().getContentType());
		assertEquals(3L, metadata.getValue().getContentLength());
	}

	@Test
	void storeResponseBody_uploadsToTheResponseBucket() {
		storage.storeResponseBody(9L, new byte[] { 5 }, "text/html");

		verify(s3).upload(eq("res-bucket"), eq("9"), any(InputStream.class), any(ObjectMetadata.class));
	}

	@Test
	void store_withoutContentType_stillUploads() {
		storage.storeResponseBody(9L, new byte[] { 5 }, null);

		ArgumentCaptor<ObjectMetadata> metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
		verify(s3).upload(eq("res-bucket"), eq("9"), any(InputStream.class), metadata.capture());
		assertEquals(null, metadata.getValue().getContentType());
	}

	@Test
	void store_failedUpload_propagates_andRegistersNoCleanup() {
		when(s3.upload(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
				.thenThrow(new IllegalStateException("s3 down"));

		assertThrows(IllegalStateException.class, () -> storage.storeRequestBody(7L, new byte[] { 1 }, "a/b"));

		assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
	}

	// cleanup tied to the transaction

	@Test
	void store_transactionRolledBack_deletesTheObject() {
		storage.storeRequestBody(7L, new byte[] { 1 }, "a/b");

		finishTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

		verify(s3).deleteObject("req-bucket", "7");
	}

	@Test
	void store_transactionOfUnknownOutcome_deletesTheObject() {
		storage.storeResponseBody(7L, new byte[] { 1 }, "a/b");

		finishTransaction(TransactionSynchronization.STATUS_UNKNOWN);

		verify(s3).deleteObject("res-bucket", "7");
	}

	@Test
	void store_transactionCommitted_keepsTheObject() {
		storage.storeRequestBody(7L, new byte[] { 1 }, "a/b");

		finishTransaction(TransactionSynchronization.STATUS_COMMITTED);

		verify(s3, never()).deleteObject(anyString(), anyString());
	}

	@Test
	void store_cleanupFailure_doesNotThrow() {
		org.mockito.Mockito.doThrow(new IllegalStateException("s3 down")).when(s3).deleteObject("req-bucket", "7");
		storage.storeRequestBody(7L, new byte[] { 1 }, "a/b");

		finishTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
	}

	@Test
	void store_withoutATransaction_uploadsAndRegistersNothing() {
		TransactionSynchronizationManager.clearSynchronization();

		storage.storeRequestBody(7L, new byte[] { 1 }, "a/b");

		verify(s3).upload(eq("req-bucket"), eq("7"), any(InputStream.class), any(ObjectMetadata.class));
	}

	// download

	@Test
	void downloadRequestBody_returnsStreamTypeAndSize() throws Exception {
		S3Resource resource = mock(S3Resource.class);
		when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2 }));
		when(resource.contentType()).thenReturn("multipart/form-data; boundary=x");
		when(resource.contentLength()).thenReturn(2L);
		when(s3.download("req-bucket", "7")).thenReturn(resource);

		StoredBody body = storage.downloadRequestBody(7L);

		assertArrayEquals(new byte[] { 1, 2 }, body.stream().readAllBytes());
		assertEquals("multipart/form-data; boundary=x", body.contentType());
		assertEquals(2L, body.size());
	}

	@Test
	void downloadResponseBody_readsFromTheResponseBucket() throws Exception {
		S3Resource resource = mock(S3Resource.class);
		when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
		when(s3.download("res-bucket", "9")).thenReturn(resource);

		storage.downloadResponseBody(9L);

		verify(s3).download("res-bucket", "9");
	}

	@Test
	void download_missingObject_isNotFound() {
		when(s3.download("req-bucket", "7")).thenThrow(NoSuchKeyException.builder().build());

		assertThrows(EntityNotFoundException.class, () -> storage.downloadRequestBody(7L));
	}

	// delete

	@Test
	void delete_usesTheRightBucket() {
		storage.deleteRequestBody(1L);
		storage.deleteResponseBody(2L);

		verify(s3).deleteObject("req-bucket", "1");
		verify(s3).deleteObject("res-bucket", "2");
	}

	@Test
	void keyOf_isTheId() {
		assertEquals("42", BodyObjectStorage.keyOf(42L));
	}

}
