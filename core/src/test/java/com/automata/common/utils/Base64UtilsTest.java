package com.automata.common.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class Base64UtilsTest {

	private static final byte[] BINARY = { 0, 1, (byte) 0xFF, (byte) 0xFE, 10, 13, (byte) 0x80 };

	@Test
	void encodeThenDecodeToBytes_roundTripsBinaryData() {
		assertArrayEquals(BINARY, Base64Utils.decodeToBytes(Base64Utils.encode(BINARY)));
	}

	@Test
	void decodeToBytes_keepsBytesThatAreNotValidUtf8() {
		// decode(String) goes through UTF-8 and would corrupt these bytes
		assertEquals(BINARY.length, Base64Utils.decodeToBytes(Base64Utils.encode(BINARY)).length);
	}

	@Test
	void decode_returnsUtf8Text() {
		assertEquals("héllo", Base64Utils.decode(Base64Utils.encode("héllo".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
	}

}
