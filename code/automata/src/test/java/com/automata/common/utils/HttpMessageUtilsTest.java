package com.automata.common.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.automata.common.utils.HttpMessageUtils.SplitMessage;

class HttpMessageUtilsTest {

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.ISO_8859_1);
	}

	private static String text(byte[] b) {
		return new String(b, StandardCharsets.ISO_8859_1);
	}

	// split

	@Test
	void split_crlfMessage_headEndsWithBlankLine() {
		SplitMessage parts = HttpMessageUtils.split(bytes("GET / HTTP/1.1\r\nHost: a\r\n\r\nbody"));

		assertEquals("GET / HTTP/1.1\r\nHost: a\r\n\r\n", text(parts.head()));
		assertEquals("body", text(parts.body()));
	}

	@Test
	void split_bareLfMessage_isSplitToo() {
		SplitMessage parts = HttpMessageUtils.split(bytes("GET / HTTP/1.1\nHost: a\n\nbody"));

		assertEquals("GET / HTTP/1.1\nHost: a\n\n", text(parts.head()));
		assertEquals("body", text(parts.body()));
	}

	@Test
	void split_noBody_bodyIsEmpty() {
		SplitMessage parts = HttpMessageUtils.split(bytes("GET / HTTP/1.1\r\nHost: a\r\n\r\n"));

		assertEquals("GET / HTTP/1.1\r\nHost: a\r\n\r\n", text(parts.head()));
		assertEquals(0, parts.body().length);
	}

	@Test
	void split_noBlankLine_wholeMessageIsHead() {
		SplitMessage parts = HttpMessageUtils.split(bytes("GET / HTTP/1.1\r\nHost: a\r\n"));

		assertEquals("GET / HTTP/1.1\r\nHost: a\r\n", text(parts.head()));
		assertEquals(0, parts.body().length);
	}

	@Test
	void split_onlyFirstBlankLineSplits() {
		SplitMessage parts = HttpMessageUtils.split(bytes("POST / HTTP/1.1\r\nHost: a\r\n\r\nline1\r\n\r\nline2"));

		assertEquals("line1\r\n\r\nline2", text(parts.body()));
	}

	@Test
	void split_binaryBodyIsKeptByteForByte() {
		byte[] head = bytes("POST / HTTP/1.1\r\nHost: a\r\n\r\n");
		byte[] body = { 0, (byte) 0xFF, (byte) 0xFE, 10, 13, 10, 13, (byte) 0x80 };
		byte[] raw = new byte[head.length + body.length];
		System.arraycopy(head, 0, raw, 0, head.length);
		System.arraycopy(body, 0, raw, head.length, body.length);

		SplitMessage parts = HttpMessageUtils.split(raw);

		assertArrayEquals(head, parts.head());
		assertArrayEquals(body, parts.body());
	}

	@Test
	void split_emptyInput_givesEmptyParts() {
		SplitMessage parts = HttpMessageUtils.split(new byte[0]);

		assertEquals(0, parts.head().length);
		assertEquals(0, parts.body().length);
	}

	@Test
	void split_headAndBodyTogetherAreTheOriginalMessage() {
		byte[] raw = bytes("HTTP/1.1 200 OK\r\nA: b\r\n\r\n{\"a\":1}");

		SplitMessage parts = HttpMessageUtils.split(raw);

		assertEquals(text(raw), text(parts.head()) + text(parts.body()));
	}

	// encodeInlineBody

	@Test
	void encodeInlineBody_inlineBody_isBase64() {
		assertEquals(Base64Utils.encode(bytes("abc")), HttpMessageUtils.encodeInlineBody(bytes("abc"), false));
	}

	@Test
	void encodeInlineBody_bodyStoredAsFile_isNull() {
		assertNull(HttpMessageUtils.encodeInlineBody(bytes("abc"), true));
	}

	@Test
	void encodeInlineBody_emptyBody_isNull() {
		assertNull(HttpMessageUtils.encodeInlineBody(new byte[0], false));
	}

	@Test
	void encodeInlineBody_nullBody_isNull() {
		assertNull(HttpMessageUtils.encodeInlineBody(null, false));
	}

}
