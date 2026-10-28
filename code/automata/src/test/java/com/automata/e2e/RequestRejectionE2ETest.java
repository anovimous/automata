package com.automata.e2e;

import static com.automata.e2e.RawHttp.get;
import static com.automata.e2e.RawHttp.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.automata.host.Host;
import com.automata.request.common.enums.Method;

/**
 * Requests that must be refused: the answer is a 400 (or a 404 for an unknown host or tenant) with a message that
 * tells what is wrong, and nothing of the request is left in the database.
 */
@RequestE2E
class RequestRejectionE2ETest extends RequestE2ETestBase {

	record Rejected(String name, byte[] raw, String messageContains) {

		@Override
		public String toString() {
			return name;
		}
	}

	private static Arguments r(String name, RawHttp raw, String messageContains) {
		return Arguments.of(new Rejected(name, raw.bytes(), messageContains));
	}

	private static Arguments r(String name, String raw, String messageContains) {
		return Arguments.of(new Rejected(name, raw.getBytes(StandardCharsets.ISO_8859_1), messageContains));
	}

	private static String repeat(String text, int times) {
		return text.repeat(times);
	}

	private void assertNothingStored(String label) {
		em.flush();
		assertEquals(0, requestRepo.count(), label + ": no request");
		assertEquals(0, headerRepo.count(), label + ": no header");
		assertEquals(0, em.getEntityManager().createQuery("select count(q) from QueryParameter q", Long.class).getSingleResult(), label + ": no parameter");
		assertEquals(0, em.getEntityManager().createQuery("select count(v) from PathVariable v", Long.class).getSingleResult(), label + ": no path variable");
		assertEquals(0, em.getEntityManager().createQuery("select count(b) from BodyProperty b", Long.class).getSingleResult(), label + ": no body property");
		assertEquals(0, em.getEntityManager().createQuery("select count(r) from RequestRaw r", Long.class).getSingleResult(), label + ": no raw request");
		assertTrue(storedBodies.isEmpty(), label + ": nothing in the object storage");
	}

	private void assertRejected(Rejected rejected) throws Exception {
		MvcResult result = send(rejected.raw(), null, null);

		assertEquals(400, result.getResponse().getStatus(), rejected.name() + ": " + result.getResponse().getContentAsString());
		assertTrue(result.getResponse().getContentAsString().contains(rejected.messageContains()),
				rejected.name() + ": '" + result.getResponse().getContentAsString() + "' should contain '" + rejected.messageContains() + "'");
		assertNothingStored(rejected.name());
	}

	// ============================================================================================ the request line

	static Stream<Arguments> requestLines() {
		return Stream.of(
				r("an empty request", "", "The request is empty: it has no request line"),
				r("a request of one empty line", "\r\n", "The request is empty: it has no request line"),
				r("a request of empty lines", "\r\n\r\n\r\n", "The request is empty: it has no request line"),
				r("a request of bare line feeds", "\n\n", "The request is empty: it has no request line"),
				r("two empty lines before the request line", "\r\n\r\nGET /a HTTP/1.1\r\nHost: h\r\n\r\n", "The request is empty: it has no request line"),
				r("OPTIONS", RawHttp.of("OPTIONS", "/a"), "Unsupported HTTP method 'OPTIONS'"),
				r("TRACE", RawHttp.of("TRACE", "/a"), "Unsupported HTTP method 'TRACE'"),
				r("CONNECT", RawHttp.of("CONNECT", "example.com:443"), "Unsupported HTTP method 'CONNECT'"),
				r("a webdav method", RawHttp.of("PROPFIND", "/a"), "Unsupported HTTP method 'PROPFIND'"),
				r("a method that is not a method", RawHttp.of("BREW", "/a"), "Unsupported HTTP method 'BREW'"),
				r("a very long method", RawHttp.of(repeat("X", 300), "/a"), "Unsupported HTTP method"),
				r("the message lists the supported methods", RawHttp.of("OPTIONS", "/a"), "[GET, POST, PUT, PATCH, DELETE, HEAD]"),
				r("no http version", "GET /a\r\nHost: h\r\n\r\n", "Invalid protocol version"),
				r("garbage instead of a request line", "hello world", "Invalid protocol version"),
				r("http/3", get("/a").version("HTTP/3"), "Invalid protocol version"),
				r("a lower case protocol", get("/a").requestLine("GET /a http/1.1"), "Invalid protocol version"),
				r("carriage returns as the only line ending", "GET /a HTTP/1.1\rHost: h\r\r", "Invalid"),
				r("a relative target", get("a/b"), "must be a path starting with '/' or an absolute URL"),
				r("an asterisk target", get("*"), "must be a path starting with '/' or an absolute URL"),
				r("a control character in the target", "GET /a\u0001b HTTP/1.1\r\nHost: h\r\n\r\n", "contains a space or a control character"),
				r("a nul byte in the target", "GET /a\u0000b HTTP/1.1\r\nHost: h\r\n\r\n", "contains a space or a control character"),
				r("a delete character in the target", "GET /a\u007fb HTTP/1.1\r\nHost: h\r\n\r\n", "contains a space or a control character"),
				r("a header line without a colon", get("/a").rawHeaderLine("garbage line"), "Invalid header"),
				r("a space before the colon of a header", get("/a").rawHeaderLine("X-A : 1"), "Invalid header"),
				r("a header line with only a colon", get("/a").rawHeaderLine(": value"), "Invalid header"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("requestLines")
	void requestLines(Rejected rejected) throws Exception {
		assertRejected(rejected);
	}

	// ============================================================================================ characters the database cannot store

	static Stream<Arguments> nulCharacters() {
		return Stream.of(
				r("a nul in a query value", get("/a?x=%00"), "The value of the query parameter 'x' contains a NUL character (%00), which cannot be stored"),
				r("a nul in the middle of a query value", get("/a?x=ab%00cd"), "contains a NUL character"),
				r("a nul in a query name", get("/a?a%00b=1"), "The name of the query parameter"),
				r("a nul in one of many parameters", get("/a?a=1&b=2&c=%00&d=4"), "The value of the query parameter 'c'"),
				r("a nul in a form value", post("/a").form("user=a%00b"), "The value of the body property 'user' contains a NUL character"),
				r("a nul in a form name", post("/a").form("us%00er=1"), "contains a NUL character"),
				r("a nul in a json value", post("/a").json("{\"a\":\"x\\u0000y\"}"), "The value of the body property 'a' contains a NUL character"),
				r("a nul in a nested json value", post("/a").json("{\"a\":{\"b\":[\"\\u0000\"]}}"), "contains a NUL character"),
				r("a nul in a json key", post("/a").json("{\"k\\u0000\":1}"), "contains a NUL character"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("nulCharacters")
	void nulCharacters(Rejected rejected) throws Exception {
		assertRejected(rejected);
	}

	// ============================================================================================ texts that are too long

	static Stream<Arguments> limits() {
		return Stream.of(
				r("a path of 501 characters", get("/" + repeat("a", 500)), "The path is too long: 501 characters, the limit is 500"),
				r("a path of 600 characters", get("/" + repeat("a", 599)), "The path is too long: 600 characters, the limit is 500"),
				r("an encoded path that is too long", get("/" + repeat("%41", 200)), "The path is too long"),
				r("a path variable of 2001 digits", get("/items/" + repeat("7", 2001)), "A path variable is too long: 2001 characters, the limit is 2000"),
				r("a query name of 501 characters", get("/a?" + repeat("n", 501) + "=1"), "The name of the query parameter"),
				r("a query value of 2001 characters", get("/a?jwt=" + repeat("e", 2001)),
						"The value of the query parameter 'jwt' is too long: 2001 characters, the limit is 2000"),
				r("a header name of 2001 characters", get("/a").header(repeat("h", 2001), "1"), "The name of the header"),
				r("a json key of 2001 characters", post("/a").json("{\"" + repeat("k", 2001) + "\":1}"), "The path of the body property"),
				r("a deep json path of more than 2000 characters", post("/a").json(deepJson(5)), "The path of the body property"),
				r("a form name of 2001 characters", post("/a").form(repeat("k", 2001) + "=1"), "The path of the body property"),
				r("the message names the parameter and cuts it", get("/a?" + repeat("n", 501) + "=1"), "nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn..."));
	}

	/** Nested objects with keys of 450 characters: no key is too long, but the flattened path of the innermost value is. */
	private static String deepJson(int levels) {
		String key = "k".repeat(450);
		return IntStream.range(0, levels).mapToObj(i -> "{\"" + key + "\":").reduce("", String::concat) + "1" + "}".repeat(levels);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("limits")
	void limits(Rejected rejected) throws Exception {
		assertRejected(rejected);
	}

	// ============================================================================================ bodies that are not json

	static Stream<Arguments> malformedJson() {
		return Stream.of(
				r("a truncated object", post("/a").json("{\"a\":"), "Json body malformed"),
				r("a missing closing brace", post("/a").json("{\"a\":1"), "Json body malformed"),
				r("single quotes", post("/a").json("{'a':1}"), "Json body malformed"),
				r("a comment", post("/a").json("{\"a\":1 /*c*/}"), "Json body malformed"),
				r("a trailing comma", post("/a").json("{\"a\":1,}"), "Json body malformed"),
				r("nan", post("/a").json("{\"a\":NaN}"), "Json body malformed"),
				r("a leading zero", post("/a").json("{\"a\":01}"), "Json body malformed"),
				r("an unquoted key", post("/a").json("{a:1}"), "Json body malformed"),
				r("plain text with a json content type", post("/a").json("hello"), "Json body malformed"),
				r("a byte order mark", post("/a").contentType("application/json").body("\u00ef\u00bb\u00bf{\"a\":1}".getBytes(StandardCharsets.ISO_8859_1)),
						"Json body malformed"),
				r("nesting deeper than a thousand levels", post("/a").json(repeat("[", 1100) + repeat("]", 1100)), "Json body malformed"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("malformedJson")
	void malformedJson(Rejected rejected) throws Exception {
		assertRejected(rejected);
	}

	// ============================================================================================ what is lenient on purpose

	@Test
	void trailingTextAfterTheJsonDocumentIsIgnored() throws Exception {
		ingestAndVerify("trailing text", post("/a").json("{\"a\":1} trailing"),
				Expected.path("/a").method(Method.POST).inline(com.automata.request.common.enums.RequestContentType.JSON).props("a=1:INT"));
	}

	@Test
	void aBodyOfOnlyWhitespaceWithAJsonContentTypeIsAnEmptyDocument() throws Exception {
		ingestAndVerify("whitespace body", post("/a").json("   "),
				Expected.path("/a").method(Method.POST).inline(com.automata.request.common.enums.RequestContentType.JSON).props("root=null:NULL"));
	}

	// ============================================================================================ the envelope

	private MvcResult sendJson(String json) throws Exception {
		return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/requests/raw")
				.contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
	}

	@Test
	void anUnknownHostIsNotFound() throws Exception {
		String body = "{\"requestBase64\":\"" + Base64.getEncoder().encodeToString(get("/a").bytes()) + "\",\"source\":\"BURP\",\"hostId\":999999}";

		MvcResult result = sendJson(body);

		assertEquals(404, result.getResponse().getStatus());
		assertTrue(result.getResponse().getContentAsString().contains("Host not found"));
		assertNothingStored("unknown host");
	}

	@Test
	void anUnknownTenantIsNotFound() throws Exception {
		MvcResult result = send(get("/a").bytes(), 999999L, null);

		assertEquals(404, result.getResponse().getStatus());
		assertTrue(result.getResponse().getContentAsString().contains("Tenant not found"));
		assertNothingStored("unknown tenant");
	}

	@Test
	void aRequestThatIsNotBase64IsRefused() throws Exception {
		String body = "{\"requestBase64\":\"this is not base64!!\",\"source\":\"BURP\",\"hostId\":" + host.getId() + "}";

		MvcResult result = sendJson(body);

		assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
		assertNothingStored("not base64");
	}

	@Test
	void aRequestWithAnUnknownSourceIsRefused() throws Exception {
		String body = "{\"requestBase64\":\"" + Base64.getEncoder().encodeToString(get("/a").bytes()) + "\",\"source\":\"NOPE\",\"hostId\":"
				+ host.getId() + "}";

		MvcResult result = sendJson(body);

		assertTrue(result.getResponse().getStatus() >= 400, "status " + result.getResponse().getStatus());
		assertNothingStored("unknown source");
	}

	@Test
	void aBrokenJsonEnvelopeIsRefused() throws Exception {
		MvcResult result = sendJson("{\"requestBase64\":");

		assertEquals(400, result.getResponse().getStatus());
		assertNothingStored("broken envelope");
	}

	@Test
	void aTenantOfAnotherHostIsRefused() throws Exception {
		Host other = fx.host(program, "www.example.com");
		var foreign = fx.tenant(other, "mallory");
		fx.clear();

		MvcResult result = send(get("/a").bytes(), foreign.getId(), null);

		assertEquals(400, result.getResponse().getStatus());
		assertTrue(result.getResponse().getContentAsString().contains("Tenant does not belong to the host of the request"));
		assertNothingStored("tenant of another host");
	}

	@Test
	void theTenantOfTheRequestIsKept() throws Exception {
		var tenant = fx.tenant(host, "alice");
		fx.clear();

		MvcResult result = send(get("/a").bytes(), tenant.getId(), null);

		assertEquals(201, result.getResponse().getStatus());
		assertEquals(tenant.getId(), om.readTree(result.getResponse().getContentAsString()).get("tenantId").asLong());
	}

	@Test
	void aFailedRequestDoesNotBlockTheNextOne() throws Exception {
		assertEquals(400, send(RawHttp.of("OPTIONS", "/a")).getResponse().getStatus());
		assertEquals(400, send(get("/a?x=%00")).getResponse().getStatus());

		ingestAndVerify("after the failures", get("/a?x=1"), Expected.path("/a").query("x=1"));

		assertEquals(1, requestRepo.count());
	}

	@Test
	void everyRejectedRequestListIsEmptyAfterwards() throws Exception {
		List<RawHttp> bad = List.of(RawHttp.of("OPTIONS", "/a"), get("/" + repeat("a", 600)), post("/a").json("{\"a\":"), get("a/b"));

		for (RawHttp raw : bad)
			assertEquals(400, send(raw).getResponse().getStatus());

		em.flush();
		assertEquals(0, requestRepo.count());
		assertEquals(0, headerRepo.count());
	}

}
