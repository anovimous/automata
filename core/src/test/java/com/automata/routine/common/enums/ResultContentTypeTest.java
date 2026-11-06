package com.automata.routine.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ResultContentTypeTest {

	@Test
	void jsonIsServedAsApplicationJson() {
		assertEquals(MediaType.APPLICATION_JSON, ResultContentType.JSON.mediaType());
	}

	@Test
	void textIsServedAsPlainText() {
		assertEquals(MediaType.TEXT_PLAIN, ResultContentType.TEXT.mediaType());
	}

}
