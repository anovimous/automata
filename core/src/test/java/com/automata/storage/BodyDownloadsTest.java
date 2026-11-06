package com.automata.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class BodyDownloadsTest {

	private StoredBody body(String contentType, long size) {
		return new StoredBody(new ByteArrayInputStream(new byte[0]), contentType, size);
	}

	@Test
	void attachment_usesTheStoredContentType_sizeAndFilename() {
		ResponseEntity<InputStreamResource> response = BodyDownloads
				.attachment(body("multipart/form-data; boundary=x", 12), "request-5");

		assertEquals(200, response.getStatusCode().value());
		assertEquals(MediaType.parseMediaType("multipart/form-data; boundary=x"),
				response.getHeaders().getContentType());
		assertEquals(12, response.getHeaders().getContentLength());
		assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"));
		assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("request-5"));
	}

	@Test
	void mediaTypeOf_missingOrBlank_isOctetStream() {
		assertEquals(MediaType.APPLICATION_OCTET_STREAM, BodyDownloads.mediaTypeOf(null));
		assertEquals(MediaType.APPLICATION_OCTET_STREAM, BodyDownloads.mediaTypeOf("  "));
	}

	@Test
	void mediaTypeOf_invalid_isOctetStream() {
		assertEquals(MediaType.APPLICATION_OCTET_STREAM, BodyDownloads.mediaTypeOf("not a media type"));
	}

	@Test
	void mediaTypeOf_valid_isParsed() {
		assertEquals(MediaType.TEXT_HTML, BodyDownloads.mediaTypeOf("text/html"));
	}

}
