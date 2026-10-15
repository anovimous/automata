package com.automata.response;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ResponseUtilsTest {

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.ISO_8859_1);
	}

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] result = new byte[a.length + b.length];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}

	private static byte[] response(String headers, String body) {
		return bytes("HTTP/1.1 200 OK\r\n" + headers + "\r\n" + body);
	}

	// JSON: detailed components, body inline

	@Test
	void jsonBody_isBrokenIntoProperties() {
		ResponseParseResult result = ResponseUtils
				.parseResponse(response("Content-Type: application/json\r\nContent-Length: 12\r\n", "{\"a\":1,\"b\":2}"));

		assertTrue(result.validationResult().isValid());
		assertEquals(200, result.statusCode());
		assertEquals(12, result.contentLength());
		assertEquals(ResponseContentType.JSON, result.contentType());
		assertEquals(2, result.bodyParseResult().getBodyProperties().size());
		assertFalse(result.bodyStoredAsFile());
	}

	@Test
	void jsonBody_withCharset_isStillJson() {
		ResponseParseResult result = ResponseUtils
				.parseResponse(response("Content-Type: application/json; charset=utf-8\r\n", "{\"a\":1}"));

		assertEquals(ResponseContentType.JSON, result.contentType());
		assertEquals(1, result.bodyParseResult().getBodyProperties().size());
	}

	@Test
	void malformedJsonBody_isRejected() {
		byte[] raw = response("Content-Type: application/json\r\n", "{\"a\":");

		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.parseResponse(raw));
	}

	// Anything else: body goes to a file

	@Test
	void htmlBody_isStoredAsFile_andHasNoProperties() {
		ResponseParseResult result = ResponseUtils
				.parseResponse(response("Content-Type: text/html; charset=utf-8\r\n", "<html></html>"));

		assertTrue(result.validationResult().isValid());
		assertEquals(ResponseContentType.HTML, result.contentType());
		assertTrue(result.bodyStoredAsFile());
		assertTrue(result.bodyParseResult().getBodyProperties().isEmpty());
		assertEquals("text/html; charset=utf-8", result.bodyContentType());
		assertEquals("<html></html>", new String(result.rawBody(), StandardCharsets.UTF_8));
	}

	@Test
	void binaryBody_isKeptByteForByte() {
		byte[] body = { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, (byte) 0xFF };

		ResponseParseResult result = ResponseUtils
				.parseResponse(concat(response("Content-Type: image/png\r\n", ""), body));

		assertEquals(ResponseContentType.IMAGE, result.contentType());
		assertTrue(result.bodyStoredAsFile());
		assertArrayEquals(body, result.rawBody());
	}

	@Test
	void bodyWithoutContentType_isStoredAsFile_withoutAType() {
		ResponseParseResult result = ResponseUtils.parseResponse(response("", "something"));

		assertNull(result.contentType());
		assertNull(result.bodyContentType());
		assertTrue(result.bodyStoredAsFile());
	}

	// No body

	@Test
	void responseWithoutBody_hasNoFile_andNoContentType() {
		ResponseParseResult result = ResponseUtils
				.parseResponse(response("Content-Type: text/html\r\nContent-Length: 0\r\n", ""));

		assertNull(result.contentType());
		assertFalse(result.bodyStoredAsFile());
		assertEquals(0, result.rawBody().length);
	}

	// Always computed

	@Test
	void statusCodeAndHeaders_areAlwaysParsed() {
		ResponseParseResult result = ResponseUtils.parseResponse(
				bytes("HTTP/1.1 404 Not Found\r\nServer: x\r\nContent-Type: text/plain\r\n\r\nnope"));

		assertEquals(404, result.statusCode());
		assertEquals(2, result.headers().size());
		assertEquals(ResponseContentType.TEXT, result.contentType());
	}

	@Test
	void headAndBody_areReturnedSeparately() {
		byte[] raw = response("Content-Type: text/plain\r\n", "hello");

		ResponseParseResult result = ResponseUtils.parseResponse(raw);

		assertArrayEquals(raw, concat(result.rawHead(), result.rawBody()));
	}

	@Test
	void garbage_isInvalid() {
		assertFalse(ResponseUtils.parseResponse(bytes("not a response")).validationResult().isValid());
	}

}
