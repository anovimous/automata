package com.automata.request;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.RequestContentType;

class RequestUtilsTest {

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.ISO_8859_1);
	}

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] result = new byte[a.length + b.length];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}

	private static byte[] request(String requestLine, String headers, String body) {
		return bytes(requestLine + "\r\nHost: example.com\r\n" + headers + "\r\n" + body);
	}

	// JSON and FORM: detailed components

	@Test
	void jsonBody_isBrokenIntoProperties() {
		RequestParseResult result = RequestUtils.parseRawRequest(request("POST /api/users HTTP/1.1",
				"Content-Type: application/json\r\n", "{\"a\":1,\"b\":{\"c\":\"x\"}}"));

		assertTrue(result.getValidationResult().isValid());
		assertEquals(RequestContentType.JSON, result.getContentType());
		assertEquals(2, result.getBodyParseResult().getBodyProperties().size());
		assertFalse(result.isBodyStoredAsFile());
		assertEquals("application/json", result.getBodyContentType());
	}

	@Test
	void jsonBody_withCharsetParameter_isStillJson() {
		RequestParseResult result = RequestUtils.parseRawRequest(request("POST /api/users HTTP/1.1",
				"Content-Type: application/json; charset=utf-8\r\n", "{\"a\":1}"));

		assertEquals(RequestContentType.JSON, result.getContentType());
		assertEquals(1, result.getBodyParseResult().getBodyProperties().size());
	}

	@Test
	void jsonSuffixType_isParsedAsJson() {
		RequestParseResult result = RequestUtils.parseRawRequest(request("POST /api/users HTTP/1.1",
				"Content-Type: application/vnd.api+json\r\n", "{\"a\":1}"));

		assertEquals(RequestContentType.JSON, result.getContentType());
		assertEquals(1, result.getBodyParseResult().getBodyProperties().size());
	}

	@Test
	void malformedJsonBody_isRejected() {
		byte[] raw = request("POST /api/users HTTP/1.1", "Content-Type: application/json\r\n", "{\"a\":");

		assertThrows(IllegalArgumentException.class, () -> RequestUtils.parseRawRequest(raw));
	}

	@Test
	void formBody_isBrokenIntoProperties() {
		RequestParseResult result = RequestUtils.parseRawRequest(request("POST /login HTTP/1.1",
				"Content-Type: application/x-www-form-urlencoded\r\n", "user=a&pass=b"));

		assertEquals(RequestContentType.FORM, result.getContentType());
		assertEquals(2, result.getBodyParseResult().getBodyProperties().size());
		assertFalse(result.isBodyStoredAsFile());
	}

	// MULTIPART: body goes to a file

	@Test
	void multipartBody_isStoredAsFile_andHasNoProperties() {
		byte[] rawBody = concat(bytes("--XX\r\nContent-Disposition: form-data; name=\"f\"\r\n\r\n"),
				new byte[] { 0, (byte) 0xFF, (byte) 0xFE, 10, 13, (byte) 0x80 });

		byte[] raw = concat(request("POST /upload HTTP/1.1", "Content-Type: multipart/form-data; boundary=XX\r\n", ""),
				rawBody);

		RequestParseResult result = RequestUtils.parseRawRequest(raw);

		assertTrue(result.getValidationResult().isValid());
		assertEquals(RequestContentType.MULTIPART, result.getContentType());
		assertTrue(result.isBodyStoredAsFile());
		assertTrue(result.getBodyParseResult().getBodyProperties().isEmpty());
		assertEquals("multipart/form-data; boundary=XX", result.getBodyContentType());
		// bytes untouched, including the ones that are not valid UTF-8
		assertArrayEquals(rawBody, result.getRawBody());
	}

	@Test
	void multipartRequest_stillStoresTheOtherComponents() {
		byte[] raw = concat(request("POST /upload/files?x=1 HTTP/1.1",
				"Content-Type: multipart/form-data; boundary=XX\r\nX-Token: t\r\n", ""), bytes("--XX--"));

		RequestParseResult result = RequestUtils.parseRawRequest(raw);

		assertEquals(Method.POST, result.getMethod());
		assertEquals(1, result.getQueryStringParseResult().getQueryParameters().size());
		assertEquals(3, result.getHeaders().size());
	}

	// Other content types are accepted and kept in the raw only

	@Test
	void xmlBody_isAccepted_withoutProperties_andNotStoredAsFile() {
		RequestParseResult result = RequestUtils.parseRawRequest(
				request("POST /soap HTTP/1.1", "Content-Type: text/xml\r\n", "<a><b>1</b></a>"));

		assertTrue(result.getValidationResult().isValid());
		assertEquals(RequestContentType.XML, result.getContentType());
		assertTrue(result.getBodyParseResult().getBodyProperties().isEmpty());
		assertFalse(result.isBodyStoredAsFile());
		assertEquals("<a><b>1</b></a>", new String(result.getRawBody(), StandardCharsets.UTF_8));
	}

	@Test
	void unknownContentType_isAcceptedAsOther() {
		RequestParseResult result = RequestUtils.parseRawRequest(
				request("POST /x HTTP/1.1", "Content-Type: application/x-weird\r\n", "data"));

		assertTrue(result.getValidationResult().isValid());
		assertEquals(RequestContentType.OTHER, result.getContentType());
	}

	// No body / no content type

	@Test
	void requestWithoutBody_hasNoContentType_evenWithTheHeader() {
		RequestParseResult result = RequestUtils
				.parseRawRequest(request("GET /a HTTP/1.1", "Content-Type: application/json\r\n", ""));

		assertNull(result.getContentType());
		assertNull(result.getBodyContentType());
		assertEquals(0, result.getRawBody().length);
		assertFalse(result.isBodyStoredAsFile());
	}

	@Test
	void bodyWithoutContentTypeHeader_isKeptInRawOnly() {
		RequestParseResult result = RequestUtils.parseRawRequest(request("POST /a HTTP/1.1", "", "hello"));

		assertNull(result.getContentType());
		assertTrue(result.getBodyParseResult().getBodyProperties().isEmpty());
		assertEquals("hello", new String(result.getRawBody(), StandardCharsets.UTF_8));
	}

	// Head and body split

	@Test
	void headAndBody_areReturnedSeparately() {
		byte[] raw = request("POST /a HTTP/1.1", "Content-Type: application/json\r\n", "{\"a\":1}");

		RequestParseResult result = RequestUtils.parseRawRequest(raw);

		assertArrayEquals(raw, concat(result.getRawHead(), result.getRawBody()));
		assertEquals("{\"a\":1}", new String(result.getRawBody(), StandardCharsets.UTF_8));
	}

	// Version

	@Test
	void http2RequestLine_isDetected_andStillParsed() {
		RequestParseResult result = RequestUtils.parseRawRequest(request("GET /a HTTP/2", "", ""));

		assertTrue(result.getValidationResult().isValid());
		assertEquals("HTTP/2", result.getVersion());
	}

	@Test
	void http2TextInAHeader_doesNotMakeTheRequestHttp2() {
		RequestParseResult result = RequestUtils
				.parseRawRequest(request("GET /a HTTP/1.1", "Referer: see HTTP/2 spec\r\n", ""));

		assertEquals("HTTP/1.1", result.getVersion());
	}

	@Test
	void http11RequestLine_isHttp11() {
		assertEquals("HTTP/1.1", RequestUtils.parseRawRequest(request("GET /a HTTP/1.1", "", "")).getVersion());
	}

	// Invalid input

	@Test
	void garbage_isInvalid() {
		RequestParseResult result = RequestUtils.parseRawRequest(bytes("not a request"));

		assertFalse(result.getValidationResult().isValid());
	}

}
