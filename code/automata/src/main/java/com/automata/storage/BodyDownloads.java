package com.automata.storage;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public abstract class BodyDownloads {

	// The body is sent back with the Content-Type it was received with, as an
	// attachment
	public static ResponseEntity<InputStreamResource> attachment(StoredBody body, String filename) {

		return ResponseEntity.ok().contentType(mediaTypeOf(body.contentType())).contentLength(body.size())
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(filename).build().toString())
				.body(new InputStreamResource(body.stream()));

	}

	static MediaType mediaTypeOf(String contentType) {

		if (contentType == null || contentType.isBlank())
			return MediaType.APPLICATION_OCTET_STREAM;

		try {
			return MediaType.parseMediaType(contentType);
		} catch (InvalidMediaTypeException e) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}

	}

}
