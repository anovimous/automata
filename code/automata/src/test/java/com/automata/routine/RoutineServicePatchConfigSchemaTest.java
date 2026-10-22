package com.automata.routine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.routine.common.dto.RoutinePatchRequest;
import com.automata.vulnerability.VulnerabilityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RoutineServicePatchConfigSchemaTest {

	@Mock
	RoutineRepository routineRepo;
	@Mock
	VulnerabilityRepository vulnRepo;

	@InjectMocks
	RoutineService service;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private Routine routine() {
		Routine r = new Routine();
		r.setId(1L);
		r.setKey("fuzz");
		when(routineRepo.findById(1L)).thenReturn(Optional.of(r));
		when(routineRepo.save(r)).thenReturn(r);
		return r;
	}

	@Test
	void patchSetsConfigSchema() throws Exception {
		Routine r = routine();
		JsonNode schema = objectMapper.readTree("{\"type\": \"object\"}");

		RoutinePatchRequest request = new RoutinePatchRequest(Optional.empty(), false, Optional.empty(),
				Optional.empty(), Optional.empty(), null, Optional.empty(), false, Optional.of(schema), false);

		service.patchRoutine(1L, request);

		assertEquals(schema, r.getConfigSchema());
	}

	@Test
	void patchClearsConfigSchemaWhenSetNull() throws Exception {
		Routine r = routine();
		r.setConfigSchema(objectMapper.readTree("{\"type\": \"object\"}"));

		RoutinePatchRequest request = new RoutinePatchRequest(Optional.empty(), false, Optional.empty(),
				Optional.empty(), Optional.empty(), null, Optional.empty(), false, Optional.empty(), true);

		service.patchRoutine(1L, request);

		assertNull(r.getConfigSchema());
	}

	@Test
	void patchLeavesConfigSchemaUnchangedWhenNotProvided() throws Exception {
		Routine r = routine();
		JsonNode existing = objectMapper.readTree("{\"existing\": true}");
		r.setConfigSchema(existing);

		RoutinePatchRequest request = new RoutinePatchRequest(Optional.empty(), false, Optional.empty(),
				Optional.empty(), Optional.empty(), null, Optional.empty(), false, Optional.empty(), false);

		service.patchRoutine(1L, request);

		assertEquals(existing, r.getConfigSchema());
	}

}
