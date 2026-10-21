package com.automata.routine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.automata.routine.common.dto.RoutineDetailedResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class RoutineMapperTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void toDetailedResponseMapsConfigSchema() throws Exception {
		JsonNode schema = objectMapper.readTree("""
				{"type": "object", "properties": {"flags": {"type": "string"}}}
				""");
		Routine routine = new Routine();
		routine.setId(1L);
		routine.setKey("fuzz");
		routine.setConfigSchema(schema);

		RoutineDetailedResponseDto response = RoutineMapper.toDetailedResponse(routine);

		assertEquals(schema, response.configSchema());
	}

	@Test
	void toDetailedResponseMapsNullConfigSchema() {
		Routine routine = new Routine();
		routine.setId(2L);
		routine.setKey("masser");

		RoutineDetailedResponseDto response = RoutineMapper.toDetailedResponse(routine);

		assertNull(response.configSchema());
	}

}
