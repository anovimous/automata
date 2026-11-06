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

	// UTF-8 in the head

	private static byte[] utf8(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void aUtf8PathIsParsedAsText() {
		RequestParseResult result = RequestUtils.parseRawRequest(utf8("GET /café/€ HTTP/1.1\r\nHost: h\r\n\r\n"));

		assertTrue(result.getValidationResult().isValid());
		assertEquals("/café/€", result.getPathParseResult().getComputatedPath());
	}

	@Test
	void aUtf8HeaderNameIsParsedAsText() {
		RequestParseResult result = RequestUtils
				.parseRawRequest(utf8("GET /a HTTP/1.1\r\nHost: h\r\nX-é: é€😀\r\n\r\n"));

		assertTrue(result.getValidationResult().isValid());
		assertTrue(result.getHeaders().stream().anyMatch(h -> h.getPrimaryKey().getHeader().equals("X-é")),
				result.getHeaders().toString());
	}

	@Test
	void aUtf8QueryValueIsDecodedOnce() {
		RequestParseResult result = RequestUtils.parseRawRequest(utf8("GET /a?q=é%C3%A9 HTTP/1.1\r\nHost: h\r\n\r\n"));

		assertTrue(result.getValidationResult().isValid());
		assertEquals("éé", result.getQueryStringParseResult().getQueryParameters().get(0).getValue());
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "GET /caf\u00e9 HTTP/1.1\r\nHost: h\r\n\r\n",
			"GET /a HTTP/1.1\r\nHost: h\r\nX-A: caf\u00e9\r\n\r\n", "GET /a HTTP/1.1\r\nHost: h\r\nX-\u00e9: 1\r\n\r\n",
			"GET /a HTTP/1.1\r\nHost: h\r\nX-A: \u00e2\u0082\r\n\r\n", "GET /\u00c0\u00af HTTP/1.1\r\nHost: h\r\n\r\n" })
	void aHeadWhichIsNotUtf8IsRefused(String raw) {
		RequestParseResult result = RequestUtils.parseRawRequest(bytes(raw));

		assertFalse(result.getValidationResult().isValid());
		assertEquals("The request head is not valid UTF-8", result.getValidationResult().getMessage());
	}

	@Test
	void aBodyWhichIsNotUtf8IsNotLookedAtByTheHeadCheck() {
		RequestParseResult result = RequestUtils.parseRawRequest(request("POST /a HTTP/1.1", "Content-Type: text/plain\r\n", "caf\u00e9"));

		assertTrue(result.getValidationResult().isValid());
		assertEquals(4, result.getRawBody().length);
	}

	// Version

	@Test
	void http2RequestLine_isDetected_andStillParsed() {
		RequestParseResult result = RequestUtils.parseRawRequest(request("GET /a HTTP/2", "", ""));

		assertTrue(result.getValidationResult().isValid());
		assertEquals("HTTP/2.0", result.getVersion());
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource({ "HTTP/1.1,HTTP/1.1", "HTTP/1.0,HTTP/1.0", "HTTP/2,HTTP/2.0", "HTTP/2.0,HTTP/2.0",
			"HTTP/0.9,HTTP/1.1", "HTTP/1.2,HTTP/1.1", "HTTP/1.01,HTTP/1.1", "HTTP/1,HTTP/1.1",
			"HTTP/4,HTTP/1.1", "HTTP/2.1,HTTP/1.1", "HTTP/abc,HTTP/1.1" })
	void theVersionTokenIsKeptOrDefaulted(String token, String expected) {
		RequestParseResult result = RequestUtils.parseRawRequest(request("GET /a " + token, "", ""));

		assertTrue(result.getValidationResult().isValid(), token);
		assertEquals(expected, result.getVersion(), token);
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "HTTP/3", "HTTP/3.0", "HTTP/3.1" })
	void http3IsRefused(String token) {
		RequestParseResult result = RequestUtils.parseRawRequest(request("GET /a " + token, "", ""));

		assertFalse(result.getValidationResult().isValid(), token);
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "GET /a", "GET /a FOO/1.1", "GET /a HTTP", "GET /a HTP/1.1", "GET /a http/1.1", "GET /a http/1.0", "GET /a http/2",
			"GET /a http/2.0", "GET /a http/3", "GET /a Http/1.1" })
	void aTokenWhichIsNotHttpIsRefused(String requestLine) {
		RequestParseResult result = RequestUtils.parseRawRequest(request(requestLine, "", ""));

		assertFalse(result.getValidationResult().isValid(), requestLine);
	}

	@Test
	void onlyTheEndOfTheRequestLineIsTheVersion() {
		RequestParseResult result = RequestUtils.parseRawRequest(request("GET /x/HTTP/2 HTTP/1.0", "X-A: HTTP/2", ""));

		assertTrue(result.getValidationResult().isValid());
		assertEquals("HTTP/1.0", result.getVersion());
	}

	@Test
	void theEmptyLinesBeforeTheRequestLineAreSkipped() {
		RequestParseResult result = RequestUtils.parseRawRequest(("\r\nGET /a HTTP/2\r\nHost: h\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

		assertTrue(result.getValidationResult().isValid());
		assertEquals("HTTP/2.0", result.getVersion());
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
