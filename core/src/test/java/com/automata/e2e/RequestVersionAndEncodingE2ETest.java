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

/**
 * The version token of the request line (BUG-047) and the UTF-8 bytes of the head (BUG-050), from the route to the stored rows and the
 * request a job sends.
 */
@RequestE2E
class RequestVersionAndEncodingE2ETest extends RequestE2ETestBase {

	// ============================================================================================ the version

	@Test
	void theVersionOfAnHttp10RequestIsKept() throws Exception {
		ingestAndVerify("http 1.0", get("/a").version("HTTP/1.0"), Expected.path("/a").version("HTTP/1.0"));
		ingestAndVerify("http 1.0 with a body", post("/a").version("HTTP/1.0").json("{\"a\":1}"),
				Expected.path("/a").method(Method.POST).version("HTTP/1.0").inline(RequestContentType.JSON).props("a=1:INT"));
	}

	// ============================================================================================ utf-8

	@Test
	void nonAsciiBytesInThePathAreDecodedAsUtf8() throws Exception {
		ingestAndVerify("utf-8 path", get("/café/€"), Expected.path("/café/€"));
	}

	@Test
	void nonAsciiHeaderValuesAreShownAsUtf8() throws Exception {
		var summary = ingest(get("/a").header("X-Name", "é€"));
		flushAndClear();

		RequestMetadataResponse metadata = metadata(summary.requestId());

		assertEquals(new HeaderEntry("X-Name", "é€"), metadata.headers().get(1));
	}

	@Test
	void nonAsciiHeaderNamesAreStoredAsUtf8() throws Exception {
		ingest(get("/a").header("X-é", "1"));
		flushAndClear();

		assertTrue(storedHeaderNames().contains("X-é"), storedHeaderNames().toString());
	}

	@Test
	void aRawNonAsciiQueryValueIsDecodedAsUtf8() throws Exception {
		ingestAndVerify("utf-8 query", get("/a?q=é€😀"), Expected.path("/a").query("q=é€😀"));
	}

	// helper kept private to the class: the base64 of a text
	@SuppressWarnings("unused")
	private static String b64(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

}
