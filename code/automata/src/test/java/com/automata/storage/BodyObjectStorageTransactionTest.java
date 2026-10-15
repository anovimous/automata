package com.automata.storage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;

// Runs the storage inside real transactions (H2 + DataSourceTransactionManager), so the
// cleanup hook is driven by an actual commit / rollback and not triggered by hand
class BodyObjectStorageTransactionTest {

	private S3Template s3 = mock(S3Template.class);

	private BodyObjectStorage storage = new BodyObjectStorage(s3, "req-bucket", "res-bucket");

	private TransactionTemplate transaction;

	@BeforeEach
	void setUp() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:bodystorage;DB_CLOSE_DELAY=-1");
		transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
	}

	@Test
	void committedTransaction_keepsTheUploadedObjects() {
		transaction.executeWithoutResult(status -> {
			storage.storeRequestBody(1L, new byte[] { 1 }, "a/b");
			storage.storeResponseBody(2L, new byte[] { 2 }, "a/b");
		});

		verify(s3, org.mockito.Mockito.times(2)).upload(anyString(), anyString(), any(InputStream.class),
				any(ObjectMetadata.class));
		verify(s3, never()).deleteObject(anyString(), anyString());
	}

	@Test
	void exceptionAfterTheUpload_rollsBack_andDeletesTheObject() {
		assertThrows(IllegalArgumentException.class, () -> transaction.executeWithoutResult(status -> {
			storage.storeRequestBody(1L, new byte[] { 1 }, "a/b");
			// e.g. the response of the same request turned out to be malformed JSON
			throw new IllegalArgumentException("Json body malformed");
		}));

		verify(s3).deleteObject("req-bucket", "1");
	}

	@Test
	void rollbackOnly_deletesEveryObjectOfTheTransaction() {
		transaction.executeWithoutResult(status -> {
			storage.storeRequestBody(1L, new byte[] { 1 }, "a/b");
			storage.storeResponseBody(2L, new byte[] { 2 }, "a/b");
			status.setRollbackOnly();
		});

		verify(s3).deleteObject("req-bucket", "1");
		verify(s3).deleteObject("res-bucket", "2");
	}

	@Test
	void failedUpload_rollsBack_withoutDeletingAnythingElse() {
		org.mockito.Mockito.when(s3.upload(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
				.thenThrow(new IllegalStateException("s3 down"));

		assertThrows(IllegalStateException.class,
				() -> transaction.executeWithoutResult(status -> storage.storeRequestBody(1L, new byte[] { 1 }, "a/b")));

		verify(s3, never()).deleteObject(anyString(), anyString());
	}

}
