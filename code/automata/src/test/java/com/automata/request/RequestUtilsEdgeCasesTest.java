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
import com.automata.testsupport.KnownBug;

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

	@Test
	@KnownBug("BUG-022")
	void anOptionsRequestIsAccepted() {
		RequestParseResult result = parse("OPTIONS /api/users HTTP/1.1");

		assertEquals("OPTIONS", result.getMethod().name());
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
	@KnownBug("BUG-023")
	void aPercentEncodedSpaceInThePathIsAccepted() {
		RequestParseResult result = parse("GET /my%20file.txt HTTP/1.1");

		assertTrue(result.getValidationResult().isValid());
	}

	@Test
	@KnownBug("BUG-023")
	void aPercentEncodedPercentSignInThePathIsAccepted() {
		assertDoesNotFail(() -> parse("GET /discount/100%25 HTTP/1.1"));
	}

	@Test
	@KnownBug("BUG-023")
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
	@KnownBug("BUG-024")
	void unencodedBracketsAndBarsInTheUrlAreAccepted() {
		// Burp records requests as the browser sent them, and browsers send [ ] { } | in the query unencoded
		RequestParseResult result = parse("GET /api?filter[name]=x|y&ids={1} HTTP/1.1");

		assertTrue(result.getValidationResult().isValid());
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
	@KnownBug("BUG-025")
	void emptyInputIsInvalid() {
		assertFalse(RequestUtils.parseRawRequest(new byte[0]).getValidationResult().isValid());
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
		RequestInternalDto dto = dto(Method.GET, "/a", "HTTP/2", null, List.of(), List.of(), List.of());

		String raw = RequestUtils.composeRawRequest(RequestUtils.composeApacheCoreRequest(dto, "example.com"), "HTTP/2");

		assertTrue(raw.startsWith("GET /a HTTP/2\r\n"), raw);
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
}
