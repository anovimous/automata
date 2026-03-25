package com.automata.request.body;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class BodyUtilsTest {

	@Test
	void testJsonDeserializationAndSerialization() {

		String base64JsonBody = "";

		BodyParseResult parseResult = BodyUtils.parseJsonBody(base64JsonBody);

		String finalBase64JsonBody = BodyUtils.constructRawJson(parseResult.getBodyProperties());

		assertEquals(base64JsonBody, finalBase64JsonBody);

	}

}
