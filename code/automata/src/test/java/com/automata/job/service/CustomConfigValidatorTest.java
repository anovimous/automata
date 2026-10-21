package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.automata.job.domain.exception.InvalidCustomConfigException;
import com.automata.routine.Routine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class CustomConfigValidatorTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private final CustomConfigValidator validator = new CustomConfigValidator();

	private static final String FUZZ_SCHEMA = """
			{"$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object", "additionalProperties": false,
			 "required": ["rawRequestBase64"],
			 "properties": {
			  "rawRequestBase64": {"type": "string", "minLength": 1,
			   "pattern": "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$"},
			  "flags": {"type": "array", "items": {"type": "object", "additionalProperties": false,
			   "required": ["key", "value"],
			   "properties": {"key": {"enum": ["-mc", "-fc", "-t"]}, "value": {"type": "string"}}}}}}
			""";

	private static final String MASSER_SCHEMA = """
			{"$schema": "https://json-schema.org/draft/2020-12/schema", "type": ["object", "null"],
			 "additionalProperties": false,
			 "properties": {"flags": {"type": "array", "items": {"type": "object", "additionalProperties": false,
			  "required": ["key", "value"],
			  "properties": {"key": {"enum": ["-n", "-mc"]}, "value": {"type": "string"}}}}}}
			""";

	private static final String RAW_REQUEST = Base64.getEncoder().encodeToString("GET / HTTP/1.1\r\nHost: a\r\n\r\n".getBytes());

	private Routine routine(String key) {
		Routine routine = new Routine();
		routine.setKey(key);
		routine.setConfigSchema(json("fuzz".equals(key) ? FUZZ_SCHEMA : MASSER_SCHEMA));
		return routine;
	}

	private JsonNode json(String json) {
		try {
			return mapper.readTree(json);
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	private String violationsOf(Routine routine, JsonNode config) {
		return assertThrows(InvalidCustomConfigException.class, () -> validator.validate(routine, config)).getMessage();
	}

	@Test
	void routineWithoutConfigSchemaAcceptsAnything() {
		Routine routine = new Routine();
		routine.setKey("custom");

		assertDoesNotThrow(() -> validator.validate(routine, null));
		assertDoesNotThrow(() -> validator.validate(routine, json("[1, 2]")));
	}

	@Test
	void fuzzAcceptsARawRequestWithAllowedFlags() throws Exception {
		JsonNode config = json("{\"rawRequestBase64\": \"" + RAW_REQUEST + "\", \"flags\": [{\"key\": \"-mc\", \"value\": \"200\"}]}");

		assertDoesNotThrow(() -> validator.validate(routine("fuzz"), config));
	}

	@Test
	void fuzzAcceptsARawRequestWithoutFlags() throws Exception {
		assertDoesNotThrow(() -> validator.validate(routine("fuzz"), json("{\"rawRequestBase64\": \"" + RAW_REQUEST + "\"}")));
	}

	@Test
	void fuzzRejectsAMissingConfig() {
		String message = violationsOf(routine("fuzz"), null);

		assertTrue(message.contains("'fuzz'"));
	}

	@Test
	void fuzzRejectsAConfigWithoutTheRawRequest() throws Exception {
		assertTrue(violationsOf(routine("fuzz"), json("{\"flags\": []}")).contains("rawRequestBase64"));
	}

	@Test
	void fuzzRejectsARawRequestThatIsNotBase64() throws Exception {
		assertTrue(violationsOf(routine("fuzz"), json("{\"rawRequestBase64\": \"not base64!\"}")).contains("rawRequestBase64"));
	}

	@Test
	void fuzzRejectsBase64WithoutPadding() throws Exception {
		assertTrue(violationsOf(routine("fuzz"), json("{\"rawRequestBase64\": \"QUJDRA\"}")).contains("rawRequestBase64"));
	}

	@Test
	void fuzzRejectsAnUnknownFlagAndNamesIt() throws Exception {
		JsonNode config = json("{\"rawRequestBase64\": \"" + RAW_REQUEST + "\", \"flags\": [{\"key\": \"-o\", \"value\": \"/tmp/x\"}]}");

		assertTrue(violationsOf(routine("fuzz"), config).contains("flags[0].key"));
	}

	@Test
	void fuzzRejectsAFlagWithoutAValue() throws Exception {
		JsonNode config = json("{\"rawRequestBase64\": \"" + RAW_REQUEST + "\", \"flags\": [{\"key\": \"-mc\"}]}");

		assertTrue(violationsOf(routine("fuzz"), config).contains("value"));
	}

	@Test
	void fuzzRejectsAnUnknownProperty() throws Exception {
		JsonNode config = json("{\"rawRequestBase64\": \"" + RAW_REQUEST + "\", \"extra\": 1}");

		assertTrue(violationsOf(routine("fuzz"), config).contains("extra"));
	}

	@Test
	void masserAcceptsNoConfigAtAll() {
		assertDoesNotThrow(() -> validator.validate(routine("masser"), null));
	}

	@Test
	void masserAcceptsAllowedFlags() throws Exception {
		assertDoesNotThrow(() -> validator.validate(routine("masser"), json("{\"flags\": [{\"key\": \"-n\", \"value\": \"5\"}]}")));
	}

	@Test
	void masserRejectsAFlagOfFfuf() throws Exception {
		assertTrue(violationsOf(routine("masser"), json("{\"flags\": [{\"key\": \"-recursion\", \"value\": \"\"}]}")).contains("flags[0].key"));
	}

	@Test
	void masserRejectsAConfigThatIsNotAnObject() throws Exception {
		assertTrue(violationsOf(routine("masser"), json("[]")).contains("'masser'"));
	}

	@Test
	void aMalformedConfigSchemaIsReportedAsAnInternalProblem() throws Exception {
		Routine routine = new Routine();
		routine.setKey("broken");
		routine.setConfigSchema(json("{\"$ref\": \"#/$defs/missing\"}"));

		assertThrows(IllegalStateException.class, () -> validator.validate(routine, json("{}")));
	}

}
