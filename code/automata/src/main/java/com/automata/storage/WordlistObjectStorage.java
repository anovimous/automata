package com.automata.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

// The files of the wordlists, one object per wordlist, keyed by its id. The worker downloads
// them from this bucket (key = id), so the bucket and the key must stay as they are there.
@Slf4j
@Service
public class WordlistObjectStorage {

	static final String CONTENT_TYPE = "text/plain";

	private final S3Template s3Template;

	private final String bucket;

	public WordlistObjectStorage(S3Template s3Template,
			@Value("${com.automata.storage.wordlists-bucket:wordlists}") String bucket) {
		this.s3Template = s3Template;
		this.bucket = bucket;
	}

	// Tied to the surrounding transaction: when it does not commit, the uploaded object is
	// deleted again
	public void store(Long wordlistId, Path file) {

		try (InputStream in = Files.newInputStream(file)) {

			ObjectMetadata metadata = ObjectMetadata.builder().contentLength(Files.size(file))
					.contentType(CONTENT_TYPE).build();

			s3Template.upload(bucket, keyOf(wordlistId), in, metadata);

		} catch (IOException e) {
			throw new UncheckedIOException("Wordlist file could not be read for the upload", e);
		}

		this.deleteWhenNotCommitted(keyOf(wordlistId));

	}

	public StoredBody download(Long wordlistId) {

		try {

			S3Resource resource = s3Template.download(bucket, keyOf(wordlistId));

			return new StoredBody(resource.getInputStream(), CONTENT_TYPE, resource.contentLength());

		} catch (NoSuchKeyException e) {
			throw new EntityNotFoundException("Wordlist file not found in storage");
		} catch (IOException e) {
			throw new UncheckedIOException("Wordlist file could not be read from storage", e);
		}

	}

	// The object goes only when the surrounding transaction commits: a rolled back delete
	// of the wordlist leaves the wordlist with its file. A failing delete leaves an orphan object.
	public void deleteAfterCommit(Long wordlistId) {

		String key = keyOf(wordlistId);

		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			this.delete(key);
			return;
		}

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCommit() {
				delete(key);
			}

		});

	}

	static String keyOf(Long wordlistId) {
		return String.valueOf(wordlistId);
	}

	private void delete(String key) {

		try {
			s3Template.deleteObject(bucket, key);
		} catch (Exception e) {
			log.warn("Could not delete wordlist object {}/{}", bucket, key, e);
		}

	}

	private void deleteWhenNotCommitted(String key) {

		if (!TransactionSynchronizationManager.isSynchronizationActive())
			return;

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCompletion(int status) {

				if (status == STATUS_COMMITTED)
					return;

				delete(key);

			}

		});

	}

}
