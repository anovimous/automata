package com.automata.e2e;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.automata.testsupport.KnownBug;

import com.automata.job.domain.valueobject.RequestInternalDto;
import com.automata.request.RequestParseResult;
import com.automata.request.RequestUtils;
import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.dto.PathVariableInternalDto;
import com.automata.request.common.dto.QueryParameterInternalDto;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.common.enums.Source;
import com.automata.request.parameter.QueryParameterUtils;
import com.automata.request.path.PathUtils;

/**
 * Seeded random requests (no database): whatever comes in, the parser answers with a result or with an
 * IllegalArgumentException, never with another exception, and what it accepts keeps its invariants. The same
 * seeds are used on every run, and a failure names its seed and its input.
 */
class RequestParserRobustnessTest {

	private static final Pattern PLACEHOLDER = Pattern.compile("[0-9]+@(INT|GUID)");

	private static final String[] PATH_CHARS = { "a", "b", "c", "x", "Z", "0", "1", "9", "-", "_", "~", "%20", "%2F", "%25", "%41", "%zz", "%", "[", "]",
			"{", "}", "|", "^", "`", "\"", "<", ">", "\\", ";", "=", ":", "@", "!", "$", "&", "'", "(", ")", "*", "+", ",", "\u00e9" };

	private static final String[] QUERY_CHARS = { "a", "b", "Z", "0", "1", "-", "_", "~", "%20", "%26", "%3D", "%25", "%2B", "%C3%A9", "%zz", "%", "+", "[",
			"]", "{", "}", "|", "^", "\"", "<", ">", "\\", ";", ":", "@", "!", "$", "'", "(", ")", "*", ",", "/", "?" };

	private static final String[] METHODS = { "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "get", "Post", "OPTIONS", "TRACE", "CONNECT", "PROPFIND", "X",
			"" };

	private static final String[] VERSIONS = { "HTTP/1.1", "HTTP/1.1", "HTTP/1.1", "HTTP/2", "HTTP/2.0", "HTTP/1.0", "HTTP/3", "HTTP/1", "http/1.1", "" };

	private static String pick(Random random, String[] values) {
		return values[random.nextInt(values.length)];
	}

	private static String text(Random random, String[] alphabet, int maxLength) {
		StringBuilder text = new StringBuilder();
		int length = random.nextInt(maxLength + 1);
		for (int i = 0; i < length; i++)
			text.append(pick(random, alphabet));
		return text.toString();
	}

	private static String show(byte[] raw) {
		String text = new String(raw, StandardCharsets.ISO_8859_1).replace("\r", "\\r").replace("\n", "\\n").replace("\u0000", "\\0");
		return text.length() > 400 ? text.substring(0, 400) + "..." : text;
	}

	// ============================================================================================ no surprises

	/**
	 * A head of only empty lines gives the parser no request at all and it fails with a NullPointerException that
	 * names "request" (BUG-025, an empty request is a 500). Those are the only failures that may be tolerated, and
	 * only by the tests that are not about it.
	 */
	private static boolean isTheEmptyRequestBug(Throwable t) {
		return t instanceof NullPointerException && t.getMessage() != null && t.getMessage().contains("\"request\" is null");
	}

	private void assertParses(String label, byte[] raw) {
		assertParses(label, raw, true);
	}

	private void assertParses(String label, byte[] raw, boolean tolerateTheEmptyRequestBug) {
		RequestParseResult result;
		try {
			result = RequestUtils.parseRawRequest(raw);
		} catch (IllegalArgumentException e) {
			assertNotNull(e.getMessage(), label + ": a refusal explains itself <- " + show(raw));
			return;
		} catch (Throwable t) {
			if (tolerateTheEmptyRequestBug && isTheEmptyRequestBug(t))
				return;
			fail(label + ": unexpected " + t + " <- " + show(raw), t);
			return;
		}
		assertNotNull(result.getValidationResult(), label + " <- " + show(raw));
		if (!result.getValidationResult().isValid()) {
			assertNotNull(result.getValidationResult().getMessage(), label + ": an invalid result explains itself <- " + show(raw));
			assertFalse(result.getValidationResult().getMessage().isBlank(), label + " <- " + show(raw));
		}
	}

	@Test
	void randomBytesAreNeverAnUnexpectedException() {
		Random random = new Random(20261028L);
		for (int i = 0; i < 3000; i++) {
			byte[] raw = new byte[1 + random.nextInt(300)];
			random.nextBytes(raw);
			assertParses("random bytes #" + i, raw);
		}
	}

	@Test
	void randomTextWithHttpTokensIsNeverAnUnexpectedException() {
		Random random = new Random(7L);
		String[] tokens = { "GET", "POST", " ", " ", "/", "?", "&", "=", "HTTP/1.1", "HTTP/2", "\r\n", "\r\n", "\n", "\r\n\r\n", "Host: h", "Content-Type: application/json",
				"Content-Type: application/x-www-form-urlencoded", "Content-Length: 5", ":", "{", "}", "[", "]", "\"", "a", "1", "%00", "%", "\u0000", "\t" };
		for (int i = 0; i < 4000; i++) {
			StringBuilder raw = new StringBuilder();
			int count = 1 + random.nextInt(30);
			for (int j = 0; j < count; j++)
				raw.append(pick(random, tokens));
			assertParses("token soup #" + i, raw.toString().getBytes(StandardCharsets.ISO_8859_1));
		}
	}

	/** A request with a random method, target, version, headers and body: valid or not, the parser must not blow up. */
	private byte[] randomRequest(Random random, boolean onlyAcceptable) {
		String method = onlyAcceptable ? pick(random, new String[] { "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD" }) : pick(random, METHODS);
		String version = onlyAcceptable ? pick(random, new String[] { "HTTP/1.1", "HTTP/2" }) : pick(random, VERSIONS);
		String path = "/" + text(random, PATH_CHARS, 8).replace("//", "/x");
		if (onlyAcceptable && path.endsWith("/"))
			path += "a";
		String query = random.nextInt(3) == 0 ? "" : "?" + text(random, QUERY_CHARS, 12);
		if (onlyAcceptable)
			query = query.replace("%zz", "%25zz");
		String target = path + query;
		RawHttp raw = RawHttp.of(method, target).version(version);
		int headerCount = random.nextInt(5);
		for (int i = 0; i < headerCount; i++)
			raw.header("X-H" + i, text(random, new String[] { "a", "b", " ", ":", ";", "=", "\u00e9", "1" }, 6).trim());
		switch (random.nextInt(5)) {
		case 0 -> raw.json("{\"a\":" + random.nextInt(1000) + ",\"b\":[" + random.nextInt(10) + ",\"x\"],\"c\":{\"d\":null}}");
		case 1 -> raw.form(text(random, new String[] { "a", "b", "=", "&", "%20", "+", "1" }, 10));
		case 2 -> raw.contentType("text/plain").body(text(random, new String[] { "a", "\r\n", "\n\n", " " }, 8));
		case 3 -> raw.json(text(random, new String[] { "{", "}", "\"a\"", ":", "1", ",", "[", "]", "null", " " }, 8));
		default -> {
		}
		}
		return raw.bytes();
	}

	@Test
	@KnownBug("BUG-025")
	void textWithoutARequestLineIsRefusedAndNeverAnException() {
		Random random = new Random(7L);
		String[] tokens = { "\r\n", "\n", " ", "GET", "HTTP/1.1", "a", "\"", "{", "Host: h", ":" };
		for (int i = 0; i < 4000; i++) {
			StringBuilder raw = new StringBuilder();
			int count = 1 + random.nextInt(12);
			for (int j = 0; j < count; j++)
				raw.append(pick(random, tokens));
			assertParses("blank lines first #" + i, raw.toString().getBytes(StandardCharsets.ISO_8859_1), false);
		}
	}

	@Test
	void randomRequestsAreNeverAnUnexpectedException() {
		Random random = new Random(42L);
		for (int i = 0; i < 4000; i++)
			assertParses("random request #" + i, randomRequest(random, false));
	}

	// ============================================================================================ what is accepted is consistent

	@Test
	void whatIsAcceptedKeepsItsInvariants() {
		Random random = new Random(99L);
		int accepted = 0;
		for (int i = 0; i < 4000; i++) {
			byte[] raw = randomRequest(random, true);
			RequestParseResult result;
			try {
				result = RequestUtils.parseRawRequest(raw);
			} catch (IllegalArgumentException e) {
				continue;
			}
			if (!result.getValidationResult().isValid())
				continue;
			accepted++;
			String label = "request #" + i + " <- " + show(raw);

			// the bytes are kept
			ByteArrayOutputStream rebuilt = new ByteArrayOutputStream();
			rebuilt.writeBytes(result.getRawHead());
			rebuilt.writeBytes(result.getRawBody());
			assertArrayEquals(raw, rebuilt.toByteArray(), label);

			// the path
			String computed = result.getPathParseResult().getComputatedPath();
			assertNotNull(computed, label);
			assertTrue(computed.length() <= 500, label);
			String[] segments = computed.split("/", -1);
			for (int s = 0; s < segments.length; s++)
				if (PLACEHOLDER.matcher(segments[s]).matches()) {
					int index = Integer.parseInt(segments[s].substring(0, segments[s].indexOf('@')));
					assertEquals(s, index, label + ": a placeholder carries its own index");
				}
			assertEquals(segments.length > 0 ? (int) java.util.Arrays.stream(segments).filter(p -> PLACEHOLDER.matcher(p).matches()).count() : 0,
					result.getPathParseResult().getPathVariables().size(), label + ": one variable per placeholder");

			// the query
			result.getQueryStringParseResult().getQueryParameters().forEach(p -> {
				assertNotNull(p.getParameter(), label);
				assertFalse(p.getParameter().isEmpty(), label + ": no empty names");
				assertNotNull(p.getValue(), label + ": no null values");
				assertTrue(p.getParameter().indexOf('\u0000') < 0 && p.getValue().indexOf('\u0000') < 0, label + ": no nul");
			});

			// the headers and the body
			assertTrue(result.getHeaders().stream().allMatch(h -> !h.getPrimaryKey().getHeader().isBlank()), label);
			assertTrue(result.getVersion().equals("HTTP/1.1") || result.getVersion().equals("HTTP/2"), label);
			if (result.getRawBody().length == 0)
				assertTrue(result.getBodyParseResult().getBodyProperties().isEmpty(), label + ": no body, no properties");
		}
		assertTrue(accepted > 500, "the generator must produce plenty of acceptable requests, got " + accepted);
	}

	// ============================================================================================ the way back

	private static List<String> variablesOf(RequestParseResult parsed) {
		return parsed.getPathParseResult().getPathVariables().stream().map(v -> v.getIndex() + ":" + v.getType() + "=" + v.getValue()).toList();
	}

	private static List<String> queryOf(RequestParseResult parsed) {
		return parsed.getQueryStringParseResult().getQueryParameters().stream().map(q -> q.getParameter() + "=" + q.getValue()).toList();
	}

	private static List<String> propertiesOf(RequestParseResult parsed) {
		return parsed.getBodyParseResult().getBodyProperties().stream().map(b -> b.getFullPath() + "=" + b.getValue() + ":" + b.getPropertyValueType())
				.toList();
	}

	private static RequestInternalDto internalDto(RequestParseResult parsed, Method method) {
		RequestInternalDto dto = new RequestInternalDto(1L, method, parsed.getPathParseResult().getComputatedPath(), parsed.getPathParseResult().getExtension(),
				parsed.getVersion(), parsed.getBodyParseResult().getBodyProperties().size(), parsed.getContentType(), parsed.getRawBody().length, Source.BURP);
		dto.setPathVariableDtos(parsed.getPathParseResult().getPathVariables().stream().map(v -> new PathVariableInternalDto(v.getIndex(), v.getValue(), 1L)).toList());
		dto.setQueryParameterDtos(parsed.getQueryStringParseResult().getQueryParameters().stream()
				.map(q -> new QueryParameterInternalDto(q.getParameter(), q.getValue(), 1L)).toList());
		dto.setBodyPropertyDtos(parsed.getBodyParseResult().getBodyProperties().stream()
				.map(b -> new BodyPropertyInternalDto(b.getFullPath(), b.getValue(), b.getPropertyValueType(), 1L, null)).toList());
		return dto;
	}

	@Test
	void parsingWhatIsComposedFromAParsedRequestGivesTheSameMeaning() {
		Random random = new Random(2024L);
		int checked = 0;
		for (int i = 0; i < 3000; i++) {
			byte[] raw = randomRequest(random, true);
			RequestParseResult first;
			try {
				first = RequestUtils.parseRawRequest(raw);
			} catch (IllegalArgumentException e) {
				continue;
			}
			if (!first.getValidationResult().isValid())
				continue;
			boolean canBeRebuilt = first.getRawBody().length == 0 || first.getContentType() == RequestContentType.JSON
					|| first.getContentType() == RequestContentType.FORM;
			// a form body without a field has nothing to rebuild a body from
			boolean emptyForm = first.getContentType() == RequestContentType.FORM && first.getBodyParseResult().getBodyProperties().isEmpty();
			if (!canBeRebuilt || emptyForm || first.getBodyParseResult().getBodyProperties().stream().anyMatch(p -> p.getFullPath().equals("root")))
				continue;
			String label = "request #" + i + " <- " + show(raw);

			String composed = RequestUtils.composeRawRequest(RequestUtils.composeApacheCoreRequest(internalDto(first, first.getMethod()), RawHttp.HOST),
					first.getVersion());
			RequestParseResult second = RequestUtils.parseRawRequest(composed.getBytes(StandardCharsets.UTF_8));

			String why = label + " -> " + show(composed.getBytes(StandardCharsets.UTF_8));
			assertTrue(second.getValidationResult().isValid(), why + ": " + second.getValidationResult().getMessage());
			assertEquals(first.getMethod(), second.getMethod(), why);
			assertEquals(first.getVersion(), second.getVersion(), why);
			assertEquals(first.getPathParseResult().getComputatedPath(), second.getPathParseResult().getComputatedPath(), why);
			assertEquals(variablesOf(first), variablesOf(second), why);
			assertEquals(queryOf(first), queryOf(second), why);
			assertEquals(first.getContentType(), second.getContentType(), why);
			assertEquals(propertiesOf(first), propertiesOf(second), why);
			checked++;
		}
		assertTrue(checked > 500, "the generator must produce plenty of rebuildable requests, got " + checked);
	}

	// ============================================================================================ parts on their own

	@Test
	void aPathIsRebuiltExactlyAsItWasParsed() {
		Random random = new Random(5L);
		for (int i = 0; i < 5000; i++) {
			StringBuilder path = new StringBuilder();
			int segments = 1 + random.nextInt(6);
			for (int s = 0; s < segments; s++) {
				path.append('/');
				String segment = switch (random.nextInt(6)) {
				case 0 -> Integer.toString(random.nextInt(100000));
				case 1 -> java.util.UUID.randomUUID().toString();
				case 2 -> "v" + random.nextInt(10);
				default -> "s" + text(random, PATH_CHARS, 4).replace("/", "");
				};
				if (PLACEHOLDER.matcher(segment).matches())
					segment += "x";
				path.append(segment);
			}
			String label = "path #" + i + " " + path;

			var parsed = PathUtils.parsePath(path.toString());
			var rebuilt = PathUtils.composeRawPath(parsed.getComputatedPath(), parsed.getPathVariables().stream()
					.map(v -> new PathVariableInternalDto(v.getIndex(), v.getValue(), 1L)).toList());

			assertEquals(path.toString(), rebuilt, label);
		}
	}

	@Test
	void aQueryStringIsParsedTheSameAfterItWasRebuilt() {
		Random random = new Random(11L);
		for (int i = 0; i < 5000; i++) {
			String query = text(random, QUERY_CHARS, 14);
			String label = "query #" + i + " " + query;

			var first = QueryParameterUtils.parseQueryString(query);
			var rebuilt = QueryParameterUtils.composeRawQueryString(first.getQueryParameters().stream()
					.map(q -> new QueryParameterInternalDto(q.getParameter(), q.getValue(), 1L)).toList());
			var second = QueryParameterUtils.parseQueryString(rebuilt.orElse(""));

			assertEquals(first.getQueryParameters().stream().map(q -> q.getParameter() + "=" + q.getValue()).toList(),
					second.getQueryParameters().stream().map(q -> q.getParameter() + "=" + q.getValue()).toList(), label + " -> " + rebuilt.orElse(""));
		}
	}

	@Test
	void theGeneratorsCoverTheAlphabetsTheyClaim() {
		// a guard against an empty alphabet turning the tests above into no-ops
		assertTrue(PATH_CHARS.length > 30 && QUERY_CHARS.length > 30 && METHODS.length > 10 && new ArrayList<>(List.of(VERSIONS)).size() > 5);
	}

}
