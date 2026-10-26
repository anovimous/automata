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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import jakarta.persistence.EntityNotFoundException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class WordlistObjectStorageTest {

	private S3Template s3 = mock(S3Template.class);

	private WordlistObjectStorage storage = new WordlistObjectStorage(s3, "wl-bucket");

	@TempDir
	Path tmp;

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
		if (status == TransactionSynchronization.STATUS_COMMITTED)
			TransactionSynchronizationUtils.triggerAfterCommit();
		TransactionSynchronizationUtils.triggerAfterCompletion(status);
		TransactionSynchronizationManager.clearSynchronization();
	}

	private Path file(byte... content) throws IOException {
		Path file = tmp.resolve("list.txt");
		Files.write(file, content);
		return file;
	}

	// store

	@Test
	void store_uploadsTheFileToTheBucket_keyedById() throws Exception {
		// the stream is closed once the upload returns, so it is read during the upload
		java.util.List<byte[]> uploaded = new java.util.ArrayList<>();
		when(s3.upload(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
				.thenAnswer(inv -> {
					uploaded.add(inv.<InputStream>getArgument(2).readAllBytes());
					return null;
				});

		storage.store(7L, file((byte) 'a', (byte) '\n'));

		ArgumentCaptor<ObjectMetadata> metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
		verify(s3).upload(eq("wl-bucket"), eq("7"), any(InputStream.class), metadata.capture());

		assertEquals(1, uploaded.size());
		assertArrayEquals(new byte[] { 'a', '\n' }, uploaded.get(0));
		assertEquals("text/plain", metadata.getValue().getContentType());
		assertEquals(2L, metadata.getValue().getContentLength());
	}

	@Test
	void store_missingFile_isAnIoFailure_andRegistersNoCleanup() {
		assertThrows(java.io.UncheckedIOException.class, () -> storage.store(7L, tmp.resolve("missing")));

		verify(s3, never()).upload(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class));
		assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
	}

	@Test
	void store_failedUpload_propagates_andRegistersNoCleanup() throws Exception {
		when(s3.upload(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
				.thenThrow(new IllegalStateException("s3 down"));

		assertThrows(IllegalStateException.class, () -> storage.store(7L, file((byte) 'a')));

		assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
	}

	@Test
	void store_transactionRolledBack_deletesTheObject() throws Exception {
		storage.store(7L, file((byte) 'a'));

		finishTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

		verify(s3).deleteObject("wl-bucket", "7");
	}

	@Test
	void store_transactionCommitted_keepsTheObject() throws Exception {
		storage.store(7L, file((byte) 'a'));

		finishTransaction(TransactionSynchronization.STATUS_COMMITTED);

		verify(s3, never()).deleteObject(anyString(), anyString());
	}

	@Test
	void store_cleanupFailure_doesNotThrow() throws Exception {
		org.mockito.Mockito.doThrow(new IllegalStateException("s3 down")).when(s3).deleteObject("wl-bucket", "7");
		storage.store(7L, file((byte) 'a'));

		finishTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
	}

	@Test
	void store_withoutATransaction_uploadsAndRegistersNothing() throws Exception {
		TransactionSynchronizationManager.clearSynchronization();

		storage.store(7L, file((byte) 'a'));

		verify(s3).upload(eq("wl-bucket"), eq("7"), any(InputStream.class), any(ObjectMetadata.class));
	}

	// download

	@Test
	void download_returnsStreamTypeAndSize() throws Exception {
		S3Resource resource = mock(S3Resource.class);
		when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2 }));
		when(resource.contentLength()).thenReturn(2L);
		when(s3.download("wl-bucket", "7")).thenReturn(resource);

		StoredBody body = storage.download(7L);

		assertArrayEquals(new byte[] { 1, 2 }, body.stream().readAllBytes());
		assertEquals("text/plain", body.contentType());
		assertEquals(2L, body.size());
	}

	@Test
	void download_missingObject_isNotFound() {
		when(s3.download("wl-bucket", "7")).thenThrow(NoSuchKeyException.builder().build());

		assertThrows(EntityNotFoundException.class, () -> storage.download(7L));
	}

	// delete

	@Test
	void deleteAfterCommit_waitsForTheCommit() {
		storage.deleteAfterCommit(7L);

		verify(s3, never()).deleteObject(anyString(), anyString());

		finishTransaction(TransactionSynchronization.STATUS_COMMITTED);

		verify(s3).deleteObject("wl-bucket", "7");
	}

	@Test
	void deleteAfterCommit_rolledBackTransaction_keepsTheObject() {
		storage.deleteAfterCommit(7L);

		finishTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

		verify(s3, never()).deleteObject(anyString(), anyString());
	}

	@Test
	void deleteAfterCommit_withoutATransaction_deletesAtOnce() {
		TransactionSynchronizationManager.clearSynchronization();

		storage.deleteAfterCommit(7L);

		verify(s3).deleteObject("wl-bucket", "7");
	}

	@Test
	void deleteAfterCommit_failure_doesNotThrow() {
		org.mockito.Mockito.doThrow(new IllegalStateException("s3 down")).when(s3).deleteObject("wl-bucket", "7");
		storage.deleteAfterCommit(7L);

		finishTransaction(TransactionSynchronization.STATUS_COMMITTED);
	}

	@Test
	void keyOf_isTheId() {
		assertEquals("42", WordlistObjectStorage.keyOf(42L));
	}

}
