package com.automata.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class StoredBodyTest {

	@Test
	void inline_readsBackTheBytesWithTheirSizeAndContentType() throws IOException {
		byte[] body = { 1, 2, 3, (byte) 0xFF };

		StoredBody stored = StoredBody.inline(body, "image/png");

		assertEquals("image/png", stored.contentType());
		assertEquals(4, stored.size());
		assertArrayEquals(body, stored.stream().readAllBytes());
	}

	@Test
	void inline_withoutContentType_keepsItNull() {
		assertNull(StoredBody.inline(new byte[] { 1 }, null).contentType());
	}

}
