package com.automata.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.automata.job.domain.valueobject.RequestInternalDto;
import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.dto.PathVariableInternalDto;
import com.automata.request.common.dto.QueryParameterInternalDto;
import com.automata.request.common.dto.RequestFilter;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.PropertyValueType;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.common.enums.Source;

/** Parsing of the request line, URL and headers, and the composition of a request back from its parts. */
class RequestUtilsEdgeCasesTest {

	private static byte[] raw(String requestLine, String headers, String body) {
		return (requestLine + "\r\nHost: example.com\r\n" + headers + "\r\n" + body).getBytes(StandardCharsets.ISO_8859_1);
	}

	private static RequestParseResult parse(String requestLine) {
		return RequestUtils.parseRawRequest(raw(requestLine, "", ""));
	}

	private static List<String> pairs(RequestParseResult result) {
		return result.getQueryStringParseResult().getQueryParameters().stream().map(p -> p.getParameter() + "=" + p.getValue())
				.toList();
	}

	// ---- methods ----

	@ParameterizedTest
	@ValueSource(strings = { "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD" })
	void everyMethodOfTheEnumIsParsed(String method) {
		assertEquals(Method.valueOf(method), parse(method + " /a HTTP/1.1").getMethod());
	}

	@Test
	void aLowerCaseMethodIsAccepted() {
		assertEquals(Method.GET, parse("get /a HTTP/1.1").getMethod());
	}

	@ParameterizedTest
	@ValueSource(strings = { "OPTIONS", "TRACE", "CONNECT", "PROPFIND" })
	void anUnsupportedMethodIsRefusedWithAClearMessage(String method) {
		RequestParseResult result = parse(method + " /api/users HTTP/1.1");

		assertFalse(result.getValidationResult().isValid());
		assertTrue(result.getValidationResult().getMessage().startsWith("Unsupported HTTP method '" + method + "'"));
		assertTrue(result.getValidationResult().getMessage().contains("[GET, POST, PUT, PATCH, DELETE, HEAD]"));
	}

	@Test
	void aVeryLongUnsupportedMethodIsCutInTheMessage() {
		RequestParseResult result = parse("X".repeat(200) + " /a HTTP/1.1");

		assertFalse(result.getValidationResult().isValid());
		assertTrue(result.getValidationResult().getMessage().length() < 200);
	}

	// ---- path and query ----

	@Test
	void pathAndQueryAreSplitAndParsed() {
		RequestParseResult result = parse("GET /api/users/42?active=true&page=2 HTTP/1.1");

		assertEquals("/api/users/3@INT", result.getPathParseResult().getComputatedPath());
		assertEquals(List.of("active=true", "page=2"), pairs(result));
	}

	@Test
	void aRequestWithoutQueryHasNoParameters() {
		assertTrue(pairs(parse("GET /api HTTP/1.1")).isEmpty());
	}

	@Test
	void anEmptyQueryAfterTheQuestionMarkHasNoParameters() {
		assertTrue(pairs(parse("GET /api? HTTP/1.1")).isEmpty());
	}

	@Test
	void aPercentEncodedSpaceInThePathIsAcceptedAndKeptAsSent() {
		RequestParseResult result = parse("GET /my%20file.txt HTTP/1.1");

		assertTrue(result.getValidationResult().isValid());
		assertEquals("/my%20file.txt", result.getPathParseResult().getComputatedPath());
	}

	@Test
	void aPercentEncodedPercentSignInThePathIsAcceptedAndKeptAsSent() {
		RequestParseResult result = parse("GET /discount/100%25 HTTP/1.1");

		assertTrue(result.getValidationResult().isValid());
		assertEquals("/discount/100%25", result.getPathParseResult().getComputatedPath());
	}

	@Test
	void anEncodedQuestionMarkInThePathDoesNotCutIt() {
		RequestParseResult result = parse("GET /a%3Fb/c?x=1 HTTP/1.1");

		assertEquals("/a%3Fb/c", result.getPathParseResult().getComputatedPath());
		assertEquals(List.of("x=1"), pairs(result));
	}

	@Test
	void aPercentEncodedPathComposesBackToTheSameRawPath() {
		RequestParseResult result = parse("GET /my%20files/42/a%2Fb HTTP/1.1");

		assertEquals("/my%20files/42/a%2Fb", com.automata.request.path.PathUtils.composeRawPath(
				result.getPathParseResult().getComputatedPath(),
				result.getPathParseResult().getPathVariables().stream()
						.map(v -> new PathVariableInternalDto(v.getIndex(), v.getValue(), 1L)).toList()));
	}

	@Test
	void anEncodedAmpersandInAQueryValueDoesNotSplitTheParameter() {
		RequestParseResult result = parse("GET /search?q=a%26b&page=2 HTTP/1.1");

		assertEquals(List.of("q=a&b", "page=2"), pairs(result));
	}

	@Test
	void anEncodedEqualsSignInAQueryValueIsPartOfTheValue() {
		assertEquals(List.of("token=abc=="), pairs(parse("GET /x?token=abc%3D%3D HTTP/1.1")));
	}

	@Test
	void aPlusInTheQueryIsASpace() {
		assertEquals(List.of("q=a b"), pairs(parse("GET /x?q=a+b HTTP/1.1")));
	}

	@Test
	void unencodedBracketsAndBarsInTheUrlAreAccepted() {
		// Burp records requests as the browser sent them, and browsers send [ ] { } | in the query unencoded
		RequestParseResult result = parse("GET /api?filter[name]=x|y&ids={1} HTTP/1.1");

		assertTrue(result.getValidationResult().isValid());
		assertEquals(List.of("filter[name]=x|y", "ids={1}"), pairs(result));
	}

	@Test
	void unencodedSpecialCharactersInThePathAreKeptAsSent() {
		RequestParseResult result = parse("GET /a[b]/c|d/{id}/42 HTTP/1.1");

		assertTrue(result.getValidationResult().isValid());
		assertEquals("/a[b]/c|d/{id}/4@INT", result.getPathParseResult().getComputatedPath());
	}

	@Test
	void aLoneAndAMalformedPercentSignAreAccepted() {
		RequestParseResult result = parse("GET /discount/100%?a=50%&b=%zz HTTP/1.1");

		assertTrue(result.getValidationResult().isValid());
		assertEquals("/discount/100%", result.getPathParseResult().getComputatedPath());
		assertEquals(List.of("a=50%", "b=%zz"), pairs(result));
	}

	@Test
	void anAbsoluteUrlAsTheRequestTargetKeepsOnlyThePathAndTheQuery() {
		RequestParseResult result = parse("GET http://example.com/api/users/7?a=1 HTTP/1.1");

		assertEquals("/api/users/3@INT", result.getPathParseResult().getComputatedPath());
		assertEquals(List.of("a=1"), pairs(result));
	}

	@Test
	void aTargetWhichIsNotAPathIsRefusedWithAClearMessage() {
		RequestParseResult result = parse("GET foo HTTP/1.1");

		assertFalse(result.getValidationResult().isValid());
		assertTrue(result.getValidationResult().getMessage().contains("must be a path starting with '/'"));
	}

	@Test
	void everyKindOfTargetComposesBackToTheSameRequestLine() {
		for (String target : List.of("/a[b]/c|d/{id}?f[x]=1|2&ids={1}", "/discount/100%25?q=a%26b", "/my%20file?x=1")) {
			RequestParseResult parsed = parse("GET " + target + " HTTP/1.1");

			RequestInternalDto composedFrom = dto(Method.GET, parsed.getPathParseResult().getComputatedPath(), "HTTP/1.1",
					null,
					parsed.getPathParseResult().getPathVariables().stream()
							.map(v -> new PathVariableInternalDto(v.getIndex(), v.getValue(), 1L)).toList(),
					parsed.getQueryStringParseResult().getQueryParameters().stream()
							.map(p -> new QueryParameterInternalDto(p.getParameter(), p.getValue(), 1L)).toList(),
					List.of());

			String raw = RequestUtils.composeRawRequest(RequestUtils.composeApacheCoreRequest(composedFrom, "example.com"),
					"HTTP/1.1");

			// the path is as it was sent, the query is rebuilt from the parameters and percent-encoded
			String path = target.contains("?") ? target.substring(0, target.indexOf('?')) : target;
			assertTrue(raw.startsWith("GET " + path), raw);
		}
	}

	@Test
	void aSpecialCharacterInTheStoredPathIsWrittenAsItIsIntoTheRequestLine() {
		RequestInternalDto composedFrom = dto(Method.GET, "/a[b]/c|d/{id}", "HTTP/1.1", null, List.of(),
				List.of(new QueryParameterInternalDto("f[x]", "1|2", 1L)), List.of());

		String raw = RequestUtils.composeRawRequest(RequestUtils.composeApacheCoreRequest(composedFrom, "example.com"),
				"HTTP/1.1");

		assertTrue(raw.startsWith("GET /a[b]/c|d/{id}?f%5Bx%5D=1%7C2 HTTP/1.1\r\n"), raw);
	}

	@Test
	void aRequestTargetWithTooManyDotsInHostIsStillPathParsed() {
		assertEquals("/a/b", parse("GET /a/b HTTP/1.1").getPathParseResult().getComputatedPath());
	}

	// ---- headers ----

	@Test
	void everyHeaderNameIsCollected() {
		RequestParseResult result = RequestUtils.parseRawRequest(
				raw("GET /a HTTP/1.1", "Accept: */*\r\nX-Custom: 1\r\n", ""));

		List<String> names = result.getHeaders().stream().map(h -> h.getPrimaryKey().getHeader()).toList();
		assertEquals(List.of("Host", "Accept", "X-Custom"), names);
	}

	@Test
	void rawHeadKeepsTheOriginalBytes() {
		byte[] message = raw("GET /a HTTP/1.1", "X-A: 1\r\n", "");

		RequestParseResult result = RequestUtils.parseRawRequest(message);

		assertEquals("GET /a HTTP/1.1\r\nHost: example.com\r\nX-A: 1\r\n\r\n", new String(result.getRawHead(), StandardCharsets.ISO_8859_1));
		assertEquals(0, result.getRawBody().length);
	}

	@Test
	void aBodyWithoutContentTypeKeepsNoContentType() {
		RequestParseResult result = RequestUtils.parseRawRequest(raw("POST /a HTTP/1.1", "", "x=1"));

		assertNull(result.getContentType());
		assertEquals(3, result.getRawBody().length);
		assertTrue(result.getBodyParseResult().getBodyProperties().isEmpty());
	}

	@Test
	void aFormBodyParsesItsFields() {
		RequestParseResult result = RequestUtils.parseRawRequest(
				raw("POST /login HTTP/1.1", "Content-Type: application/x-www-form-urlencoded\r\n", "user=a&pass=b%26c"));

		assertEquals(RequestContentType.FORM, result.getContentType());
		assertEquals(2, result.getBodyParseResult().getBodyProperties().size());
		assertEquals("b&c", result.getBodyParseResult().getBodyProperties().get(1).getValue());
	}

	@Test
	void emptyInputIsInvalid() {
		RequestParseResult result = RequestUtils.parseRawRequest(new byte[0]);

		assertFalse(result.getValidationResult().isValid());
		assertEquals("The request is empty: it has no request line", result.getValidationResult().getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = { "\r\n", "\n", "\r\n\r\n", "\n\n\n", "\r\n\r\nGET /a HTTP/1.1\r\nHost: h\r\n\r\n" })
	void emptyLinesWithoutARequestLineAreInvalid(String input) {
		RequestParseResult result = RequestUtils.parseRawRequest(input.getBytes(StandardCharsets.ISO_8859_1));

		assertFalse(result.getValidationResult().isValid());
		assertEquals("The request is empty: it has no request line", result.getValidationResult().getMessage());
	}

	@Test
	void oneEmptyLineBeforeTheRequestLineIsSkipped() {
		RequestParseResult result = RequestUtils.parseRawRequest("\r\nGET /a HTTP/1.1\r\nHost: h\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));

		assertTrue(result.getValidationResult().isValid());
		assertEquals("/a", result.getPathParseResult().getComputatedPath());
	}

	// ---- validateRequestFilter ----

	@Test
	void aNullFilterIsInvalid() {
		assertFalse(RequestUtils.validateRequestFilter(null).isValid());
	}

	@Test
	void aFilterWithoutHostOrProgramIsInvalid() {
		RequestFilter filter = new RequestFilter();
		filter.setMethod(Method.GET);

		assertFalse(RequestUtils.validateRequestFilter(filter).isValid());
	}

	@Test
	void aFilterWithAHostOrAProgramIsValid() {
		RequestFilter withHost = new RequestFilter();
		withHost.setHostId(1L);
		RequestFilter withProgram = new RequestFilter();
		withProgram.setProgramId(1L);

		assertTrue(RequestUtils.validateRequestFilter(withHost).isValid());
		assertTrue(RequestUtils.validateRequestFilter(withProgram).isValid());
	}

	// ---- composition ----

	private static RequestInternalDto dto(Method method, String path, String version, RequestContentType contentType,
			List<PathVariableInternalDto> vars, List<QueryParameterInternalDto> params, List<BodyPropertyInternalDto> props) {
		RequestInternalDto dto = new RequestInternalDto(1L, method, path, null, version, 0, contentType, 0, Source.BURP);
		dto.setPathVariableDtos(vars);
		dto.setQueryParameterDtos(params);
		dto.setBodyPropertyDtos(props);
		return dto;
	}

	@Test
	void composedGetRequestHasTheRequestLineAndTheHostHeader() {
		RequestInternalDto dto = dto(Method.GET, "/api/users/3@INT", "HTTP/1.1", null,
				List.of(new PathVariableInternalDto(3, "42", 1L)), List.of(new QueryParameterInternalDto("a", "1", 1L)), List.of());

		ClassicHttpRequest request = RequestUtils.composeApacheCoreRequest(dto, "example.com");
		String raw = RequestUtils.composeRawRequest(request, "HTTP/1.1");

		assertTrue(raw.startsWith("GET /api/users/42?a=1 HTTP/1.1\r\n"), raw);
		assertTrue(raw.contains("Host: example.com\r\n"), raw);
		assertFalse(raw.contains("Content-Type"), raw);
	}

	@Test
	void composedJsonRequestCarriesTheBodyAndTheContentType() {
		RequestInternalDto dto = dto(Method.POST, "/api/users", "HTTP/1.1", RequestContentType.JSON, List.of(), List.of(),
				List.of(new BodyPropertyInternalDto("name", "bob", PropertyValueType.STRING, 1L, null),
						new BodyPropertyInternalDto("age", "30", PropertyValueType.INT, 1L, null)));

		String raw = RequestUtils.composeRawRequest(RequestUtils.composeApacheCoreRequest(dto, "example.com"), "HTTP/1.1");

		assertTrue(raw.contains("Content-Type: application/json"), raw);
		assertTrue(raw.endsWith("{\"name\":\"bob\",\"age\":30}"), raw);
	}

	@Test
	void composedFormRequestCarriesTheEncodedBody() {
		RequestInternalDto dto = dto(Method.POST, "/login", "HTTP/1.1", RequestContentType.FORM, List.of(), List.of(),
				List.of(new BodyPropertyInternalDto("user", "a b", PropertyValueType.STRING, 1L, null)));

		String raw = RequestUtils.composeRawRequest(RequestUtils.composeApacheCoreRequest(dto, "example.com"), "HTTP/1.1");

		assertTrue(raw.contains("Content-Type: application/x-www-form-urlencoded"), raw);
		assertTrue(raw.endsWith("user=a+b"), raw);
	}

	@Test
	void composedHttp2RequestIsWrittenWithTheHttp2Version() {
		RequestInternalDto dto = dto(Method.GET, "/a", "HTTP/2.0", null, List.of(), List.of(), List.of());

		String raw = RequestUtils.composeRawRequest(RequestUtils.composeApacheCoreRequest(dto, "example.com"), "HTTP/2.0");

		assertTrue(raw.startsWith("GET /a HTTP/2.0\r\n"), raw);
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "HTTP/1.0", "HTTP/1.1", "HTTP/2.0" })
	void aComposedRequestIsWrittenWithItsVersion(String version) {
		RequestInternalDto dto = dto(Method.GET, "/a", version, null, List.of(), List.of(), List.of());

		String raw = RequestUtils.composeRawRequest(RequestUtils.composeApacheCoreRequest(dto, "example.com"), version);

		assertTrue(raw.startsWith("GET /a " + version + "\r\n"), raw);
	}

	@Test
	void aUtf8PathIsComposedAsItsUtf8Bytes() {
		RequestInternalDto dto = dto(Method.GET, "/café/€", "HTTP/1.1", null, List.of(), List.of(), List.of());

		String raw = RequestUtils.composeRawRequest(RequestUtils.composeApacheCoreRequest(dto, "example.com"), "HTTP/1.1");

		assertTrue(raw.startsWith("GET /café/€ HTTP/1.1\r\n"), raw);
	}

	@Test
	void aPathWhichHoldsTheDefaultVersionIsNotTouchedWhenComposing() {
		RequestInternalDto dto = dto(Method.GET, "/HTTP/1.1/x", "HTTP/1.0", null, List.of(), List.of(), List.of());

		String raw = RequestUtils.composeRawRequest(RequestUtils.composeApacheCoreRequest(dto, "example.com"), "HTTP/1.0");

		assertTrue(raw.startsWith("GET /HTTP/1.1/x HTTP/1.0\r\n"), raw);
	}

	@Test
	void aJsonRequestWithoutBodyPropertiesIsComposedWithAnEmptyObject() {
		RequestInternalDto dto = dto(Method.POST, "/a", "HTTP/1.1", RequestContentType.JSON, List.of(), List.of(), List.of());

		ClassicHttpRequest request = RequestUtils.composeApacheCoreRequest(dto, "example.com");

		assertNotNull(request);
	}

	@Test
	void aBodyOfAnotherContentTypeCannotBeComposedFromItsProperties() {
		RequestInternalDto dto = dto(Method.POST, "/a", "HTTP/1.1", RequestContentType.XML, List.of(), List.of(), List.of());

		assertThrows(IllegalArgumentException.class, () -> RequestUtils.composeApacheCoreRequest(dto, "example.com"));
	}

	private static void assertDoesNotFail(Runnable runnable) {
		RequestParseResult result = null;
		try {
			runnable.run();
		} catch (RuntimeException e) {
			throw new AssertionError("parsing threw " + e, e);
		}
		assertNull(result);
	}

	// ---- the size of the texts taken from the request ----

	private static String invalidMessage(RequestParseResult result) {
		assertFalse(result.getValidationResult().isValid());
		return result.getValidationResult().getMessage();
	}

	@Test
	void aComputatedPathAtTheLimitIsAccepted() {
		String path = "/" + "a".repeat(499);

		assertTrue(parse("GET " + path + " HTTP/1.1").getValidationResult().isValid());
	}

	@Test
	void aComputatedPathOverTheLimitIsRefused() {
		String path = "/" + "a".repeat(500);

		assertEquals("The path is too long: 501 characters, the limit is 500",
				invalidMessage(parse("GET " + path + " HTTP/1.1")));
	}

	@Test
	void aQueryValueAtTheLimitIsAcceptedAndOneOverItIsRefused() {
		assertTrue(parse("GET /a?jwt=" + "e".repeat(2000) + " HTTP/1.1").getValidationResult().isValid());

		assertEquals("The value of the query parameter 'jwt' is too long: 2001 characters, the limit is 2000",
				invalidMessage(parse("GET /a?jwt=" + "e".repeat(2001) + " HTTP/1.1")));
	}

	@Test
	void aQueryParameterNameOverTheLimitIsRefusedAndCutInTheMessage() {
		String message = invalidMessage(parse("GET /a?" + "n".repeat(501) + "=1 HTTP/1.1"));

		assertTrue(message.startsWith("The name of the query parameter '" + "n".repeat(40) + "...' is too long: 501"));
	}

	@Test
	void aPathVariableOverTheLimitIsRefused() {
		// a long run of digits is a path variable, and it is also part of the computed path only as a placeholder
		assertEquals("A path variable is too long: 2001 characters, the limit is 2000",
				invalidMessage(parse("GET /items/" + "7".repeat(2001) + " HTTP/1.1")));
	}

	@Test
	void aHeaderNameOverTheLimitIsRefused() {
		RequestParseResult result = RequestUtils
				.parseRawRequest(raw("GET /a HTTP/1.1", "X-" + "h".repeat(2000) + ": 1\r\n", ""));

		assertTrue(invalidMessage(result).startsWith("The name of the header 'X-hhhh"));
	}

	@Test
	void aBodyPropertyPathOverTheLimitIsRefused() {
		String body = "{\"" + "k".repeat(2001) + "\":1}";
		RequestParseResult result = RequestUtils.parseRawRequest(
				raw("POST /a HTTP/1.1", "Content-Type: application/json\r\nContent-Length: " + body.length() + "\r\n", body));

		assertTrue(invalidMessage(result).contains("The path of the body property"));
	}

	@Test
	void everythingWithinTheLimitsIsKept() {
		RequestParseResult result = parse("GET /a/b.json?x=1&y=2 HTTP/1.1");

		assertTrue(result.getValidationResult().isValid());
	}

	// ---- NUL characters ----

	@Test
	void aNulCharacterInAQueryValueIsRefusedBecauseTheDatabaseCannotStoreIt() {
		assertEquals("The value of the query parameter 'x' contains a NUL character (%00), which cannot be stored",
				invalidMessage(parse("GET /a?x=%00 HTTP/1.1")));
	}

	@Test
	void aNulCharacterInAQueryParameterNameIsRefused() {
		assertEquals("The name of the query parameter 'a\u0000b' contains a NUL character (%00), which cannot be stored",
				invalidMessage(parse("GET /a?a%00b=1 HTTP/1.1")));
	}

	@Test
	void aNulCharacterInAFormFieldIsRefused() {
		String body = "user=a%00b";
		RequestParseResult result = RequestUtils.parseRawRequest(raw("POST /a HTTP/1.1",
				"Content-Type: application/x-www-form-urlencoded\r\nContent-Length: " + body.length() + "\r\n", body));

		assertTrue(invalidMessage(result).contains("The value of the body property 'user' contains a NUL character"));
	}

	@Test
	void aNulCharacterInAJsonValueIsRefused() {
		String body = "{\"a\":\"x\\u0000y\"}";
		RequestParseResult result = RequestUtils.parseRawRequest(raw("POST /a HTTP/1.1",
				"Content-Type: application/json\r\nContent-Length: " + body.length() + "\r\n", body));

		assertTrue(invalidMessage(result).contains("The value of the body property 'a' contains a NUL character"));
	}

	@Test
	void anEncodedNulCharacterInThePathIsKeptAsText() {
		// the path is not decoded, so %00 stays three characters and can be stored
		RequestParseResult result = parse("GET /a%00b HTTP/1.1");

		assertTrue(result.getValidationResult().isValid());
		assertEquals("/a%00b", result.getPathParseResult().getComputatedPath());
	}
}
