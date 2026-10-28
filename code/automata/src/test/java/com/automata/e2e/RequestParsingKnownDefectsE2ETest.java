package com.automata.e2e;

import static com.automata.e2e.RawHttp.get;
import static com.automata.e2e.RawHttp.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.automata.common.dto.HeaderEntry;
import com.automata.request.common.dto.RequestMetadataResponse;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.RequestContentType;
import com.automata.testsupport.KnownBug;

/**
 * What the parsing of a request must do and does not do yet. Every test states the correct behaviour and is
 * disabled with the bug that breaks it (see .claude/functionality-bugs.md); when a bug is fixed, its tests
 * lose the annotation and guard the fix. To see them all fail, run the tests with
 * {@code -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'}.
 */
@RequestE2E
class RequestParsingKnownDefectsE2ETest extends RequestE2ETestBase {

	// ============================================================================================ BUG-019

	@Test
	@KnownBug("BUG-019")
	void theRootPathKeepsItsSlash() throws Exception {
		ingestAndVerify("root", get("/"), Expected.path("/"));
		ingestAndVerify("root with a query", get("/?x=1"), Expected.path("/").query("x=1"));
		ingestAndVerify("an absolute url without a path", get("http://h"), Expected.path("/"));
		ingestAndVerify("an absolute url with only a query", get("http://h?x=1"), Expected.path("/").query("x=1"));
	}

	@Test
	@KnownBug("BUG-019")
	void aTrailingSlashIsKept() throws Exception {
		ingestAndVerify("trailing slash", get("/api/users/"), Expected.path("/api/users/"));
		ingestAndVerify("trailing slash after a number", get("/api/users/42/"), Expected.path("/api/users/3@INT/").vars("3:INT=42"));
	}

	// ============================================================================================ BUG-047

	@Test
	@KnownBug("BUG-047")
	void theVersionOfAnHttp10RequestIsKept() throws Exception {
		ingestAndVerify("http 1.0", get("/a").version("HTTP/1.0"), Expected.path("/a").version("HTTP/1.0"));
		ingestAndVerify("http 1.0 with a body", post("/a").version("HTTP/1.0").json("{\"a\":1}"),
				Expected.path("/a").method(Method.POST).version("HTTP/1.0").inline(RequestContentType.JSON).props("a=1:INT"));
	}

	// ============================================================================================ BUG-050

	@Test
	@KnownBug("BUG-050")
	void nonAsciiBytesInThePathAreDecodedAsUtf8() throws Exception {
		ingestAndVerify("utf-8 path", get("/café/€"), Expected.path("/café/€"));
	}

	@Test
	@KnownBug("BUG-050")
	void nonAsciiHeaderValuesAreShownAsUtf8() throws Exception {
		var summary = ingest(get("/a").header("X-Name", "é€"));
		flushAndClear();

		RequestMetadataResponse metadata = metadata(summary.requestId());

		assertEquals(new HeaderEntry("X-Name", "é€"), metadata.headers().get(1));
	}

	@Test
	@KnownBug("BUG-050")
	void nonAsciiHeaderNamesAreStoredAsUtf8() throws Exception {
		ingest(get("/a").header("X-é", "1"));
		flushAndClear();

		assertTrue(storedHeaderNames().contains("X-é"), storedHeaderNames().toString());
	}

	// helper kept private to the class: the base64 of a text
	@SuppressWarnings("unused")
	private static String b64(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

}
