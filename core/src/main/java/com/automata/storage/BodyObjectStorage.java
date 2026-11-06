package com.automata.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

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

// Request and response bodies that are too unwieldy for the database, one object per
// request / response, keyed by its id
@Slf4j
@Service
public class BodyObjectStorage {

	private final S3Template s3Template;

	private final String requestBodiesBucket;

	private final String responseBodiesBucket;

	public BodyObjectStorage(S3Template s3Template,
			@Value("${com.automata.storage.request-bodies-bucket:request-bodies}") String requestBodiesBucket,
			@Value("${com.automata.storage.response-bodies-bucket:response-bodies}") String responseBodiesBucket) {
		this.s3Template = s3Template;
		this.requestBodiesBucket = requestBodiesBucket;
		this.responseBodiesBucket = responseBodiesBucket;
	}

	// Storing is tied to the surrounding transaction: when it does not commit, the
	// uploaded object is deleted again

	public void storeRequestBody(Long requestId, byte[] body, String contentType) {
		this.store(requestBodiesBucket, keyOf(requestId), body, contentType);
	}

	public void storeResponseBody(Long responseId, byte[] body, String contentType) {
		this.store(responseBodiesBucket, keyOf(responseId), body, contentType);
	}

	public StoredBody downloadRequestBody(Long requestId) {
		return this.download(requestBodiesBucket, keyOf(requestId));
	}

	public StoredBody downloadResponseBody(Long responseId) {
		return this.download(responseBodiesBucket, keyOf(responseId));
	}

	public void deleteRequestBody(Long requestId) {
		s3Template.deleteObject(requestBodiesBucket, keyOf(requestId));
	}

	public void deleteResponseBody(Long responseId) {
		s3Template.deleteObject(responseBodiesBucket, keyOf(responseId));
	}

	static String keyOf(Long id) {
		return String.valueOf(id);
	}

	private void store(String bucket, String key, byte[] body, String contentType) {

		ObjectMetadata.Builder metadata = ObjectMetadata.builder().contentLength((long) body.length);

		if (contentType != null && !contentType.isBlank())
			metadata.contentType(contentType);

		s3Template.upload(bucket, key, new ByteArrayInputStream(body), metadata.build());

		this.deleteWhenNotCommitted(bucket, key);

	}

	private StoredBody download(String bucket, String key) {

		try {

			S3Resource resource = s3Template.download(bucket, key);

			return new StoredBody(resource.getInputStream(), resource.contentType(), resource.contentLength());

		} catch (NoSuchKeyException e) {
			throw new EntityNotFoundException("Body file not found in storage");
		} catch (IOException e) {
			throw new UncheckedIOException("Body file could not be read from storage", e);
		}

	}

	private void deleteWhenNotCommitted(String bucket, String key) {

		if (!TransactionSynchronizationManager.isSynchronizationActive())
			return;

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

			@Override
			public void afterCompletion(int status) {

				if (status == STATUS_COMMITTED)
					return;

				try {
					s3Template.deleteObject(bucket, key);
				} catch (Exception e) {
					log.warn("Could not delete body object {}/{} after the transaction did not commit", bucket, key, e);
				}

			}

		});

	}

}
