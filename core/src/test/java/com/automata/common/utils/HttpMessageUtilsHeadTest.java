package com.automata.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.automata.common.dto.BodyStorage;
import com.automata.common.dto.HeaderEntry;

class HttpMessageUtilsHeadTest {

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.ISO_8859_1);
	}

	// startLine

	@Test
	void startLine_isTheFirstLineWithoutItsEnding() {
		assertEquals("GET /a HTTP/1.1", HttpMessageUtils.startLine(bytes("GET /a HTTP/1.1\r\nHost: a\r\n\r\n")));
	}

	@Test
	void startLine_acceptsBareLineFeeds() {
		assertEquals("HTTP/1.1 200 OK", HttpMessageUtils.startLine(bytes("HTTP/1.1 200 OK\nServer: x\n\n")));
	}

	@Test
	void startLine_emptyHead_isEmpty() {
		assertEquals("", HttpMessageUtils.startLine(new byte[0]));
	}

	// requestTarget

	@Test
	void requestTarget_isTheSecondTokenOfTheStartLine() {
		assertEquals("/api/u?x=1", HttpMessageUtils.requestTarget(bytes("POST /api/u?x=1 HTTP/1.1\r\n\r\n")));
	}

	@Test
	void requestTarget_http2StartLine() {
		assertEquals("/a", HttpMessageUtils.requestTarget(bytes("GET /a HTTP/2\r\n\r\n")));
	}

	@Test
	void requestTarget_startLineWithoutTarget_isNull() {
		assertNull(HttpMessageUtils.requestTarget(bytes("GET\r\n\r\n")));
	}

	// parseHeaders

	@Test
	void parseHeaders_keepsOrderNamesAndValues() {
		List<HeaderEntry> headers = HttpMessageUtils
				.parseHeaders(bytes("GET /a HTTP/1.1\r\nHost: a.com\r\nAccept:*/*\r\nX-Dup: 1\r\nX-Dup: 2\r\n\r\n"));

		assertEquals(List.of(new HeaderEntry("Host", "a.com"), new HeaderEntry("Accept", "*/*"),
				new HeaderEntry("X-Dup", "1"), new HeaderEntry("X-Dup", "2")), headers);
	}

	@Test
	void parseHeaders_valueKeepsItsOwnColons() {
		List<HeaderEntry> headers = HttpMessageUtils.parseHeaders(bytes("GET /a HTTP/1.1\r\nReferer: http://a.com:80/x\r\n\r\n"));

		assertEquals(List.of(new HeaderEntry("Referer", "http://a.com:80/x")), headers);
	}

	@Test
	void parseHeaders_stopsAtTheBlankLine() {
		List<HeaderEntry> headers = HttpMessageUtils.parseHeaders(bytes("GET /a HTTP/1.1\r\nHost: a\r\n\r\nNot-A-Header: x"));

		assertEquals(List.of(new HeaderEntry("Host", "a")), headers);
	}

	@Test
	void parseHeaders_foldedLineContinuesThePreviousValue() {
		List<HeaderEntry> headers = HttpMessageUtils.parseHeaders(bytes("GET /a HTTP/1.1\r\nX-Long: one\r\n  two\r\n\r\n"));

		assertEquals(List.of(new HeaderEntry("X-Long", "one two")), headers);
	}

	@Test
	void parseHeaders_skipsLinesWithoutAName() {
		List<HeaderEntry> headers = HttpMessageUtils.parseHeaders(bytes("GET /a HTTP/1.1\r\nnonsense\r\n: empty\r\nHost: a\r\n\r\n"));

		assertEquals(List.of(new HeaderEntry("Host", "a")), headers);
	}

	@Test
	void parseHeaders_startLineOnly_hasNoHeaders() {
		assertTrue(HttpMessageUtils.parseHeaders(bytes("GET /a HTTP/1.1\r\n\r\n")).isEmpty());
	}

	// headerValue / contentTypeOfHead

	@Test
	void headerValue_isCaseInsensitiveAndReturnsTheFirstMatch() {
		List<HeaderEntry> headers = List.of(new HeaderEntry("content-type", "a/b"), new HeaderEntry("Content-Type", "c/d"));

		assertEquals("a/b", HttpMessageUtils.headerValue(headers, "Content-Type"));
	}

	@Test
	void headerValue_missing_isNull() {
		assertNull(HttpMessageUtils.headerValue(List.of(new HeaderEntry("Host", "a")), "Content-Type"));
	}

	@Test
	void contentTypeOfHead_keepsTheParameters() {
		assertEquals("text/html; charset=utf-8", HttpMessageUtils
				.contentTypeOfHead(bytes("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n")));
	}

	@Test
	void contentTypeOfHead_noHeader_isNull() {
		assertNull(HttpMessageUtils.contentTypeOfHead(bytes("HTTP/1.1 200 OK\r\n\r\n")));
	}

	// bodyStorageOf

	@Test
	void bodyStorageOf_emptyBody_isNone_evenIfFlaggedAsFile() {
		assertEquals(BodyStorage.NONE, HttpMessageUtils.bodyStorageOf(0, false));
		assertEquals(BodyStorage.NONE, HttpMessageUtils.bodyStorageOf(0, true));
	}

	@Test
	void bodyStorageOf_fileFlag_decidesBetweenFileAndInline() {
		assertEquals(BodyStorage.FILE, HttpMessageUtils.bodyStorageOf(10, true));
		assertEquals(BodyStorage.INLINE, HttpMessageUtils.bodyStorageOf(10, false));
	}

}
