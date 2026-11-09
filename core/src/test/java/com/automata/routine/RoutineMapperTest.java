package com.automata.routine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.automata.routine.common.dto.RoutineDetailedResponseDto;
import com.automata.routine.common.dto.RoutineSummaryResponseDto;
import com.automata.routine.common.enums.ResultContentType;
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
	void toDetailedResponseMapsResultContentType() {
		Routine routine = new Routine();
		routine.setResultContentType(ResultContentType.TEXT);

		assertEquals(ResultContentType.TEXT, RoutineMapper.toDetailedResponse(routine).resultContentType());
	}

	@Test
	void toDetailedResponseMapsNullConfigSchema() {
		Routine routine = new Routine();
		routine.setId(2L);
		routine.setKey("masser");

		RoutineDetailedResponseDto response = RoutineMapper.toDetailedResponse(routine);

		assertNull(response.configSchema());
	}

	@Test
	void theDetailedResponseCarriesTheDescriptionAndTheUsageDescriptionForAgent() {
		Routine routine = new Routine();
		routine.setDescription("ffuf fuzzer");
		routine.setUsageDescriptionForAgent("Use it to fuzz one request");

		RoutineDetailedResponseDto response = RoutineMapper.toDetailedResponse(routine);

		assertEquals("ffuf fuzzer", response.description());
		assertEquals("Use it to fuzz one request", response.usageDescriptionForAgent());
	}

	@Test
	void theSummaryResponseHasNoUsageDescriptionForAgent() {
		List<String> fields = Arrays.stream(RoutineSummaryResponseDto.class.getRecordComponents())
				.map(RecordComponent::getName).toList();

		assertFalse(fields.contains("usageDescriptionForAgent"));
		assertFalse(fields.contains("description"));
	}

	@Test
	void theWordlistRequirementIsMappedInTheDetailedAndTheSummaryResponse() {
		Routine routine = new Routine();
		routine.setWordlistRequired(true);

		assertEquals(true, RoutineMapper.toDetailedResponse(routine).wordlistRequired());
		assertEquals(true, RoutineMapper.toSummaryResponse(routine).wordlistRequired());
		assertEquals(false, RoutineMapper.toSummaryResponse(new Routine()).wordlistRequired());
	}

}
