package com.automata.routine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.automata.routine.common.enums.TargetCardinalityPair;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RoutineControllerCreateTest {

	@Mock
	RoutineService routineService;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
		mvc = MockMvcBuilders.standaloneSetup(new RoutineController(routineService, mapper)).build();
	}

	private String body(boolean available, boolean wordlistRequired) {
		return "{\"key\":\"fuzz\",\"isAvailableAtConsumer\":" + available + ",\"wordlistRequired\":" + wordlistRequired
				+ ",\"protocol\":\"HTTP\",\"allowedTargets\":[\"SINGLE_REQUEST\"],\"resultContentType\":\"JSON\"}";
	}

	private Routine created(boolean available, boolean wordlistRequired) throws Exception {
		Routine saved = new Routine();
		saved.setId(7L);
		when(routineService.createRoutine(any(), any())).thenReturn(saved);

		mvc.perform(post("/api/routines").contentType(MediaType.APPLICATION_JSON).content(body(available, wordlistRequired)))
				.andExpect(status().isCreated()).andExpect(header().string("Location", "/api/routines/7"));

		ArgumentCaptor<Routine> routine = ArgumentCaptor.forClass(Routine.class);
		verify(routineService).createRoutine(routine.capture(), any());
		return routine.getValue();
	}

	@Test
	void createKeepsTheAvailabilityAndTheWordlistRequirementOfTheRequest() throws Exception {
		Routine routine = created(true, true);

		assertTrue(routine.isAvailableAtConsumer());
		assertTrue(routine.isWordlistRequired());
		assertEquals("fuzz", routine.getKey());
		assertEquals(java.util.Set.of(TargetCardinalityPair.SINGLE_REQUEST), routine.getAllowedTargets());
	}

	@Test
	void createKeepsFalseValues() throws Exception {
		Routine routine = created(false, false);

		assertFalse(routine.isAvailableAtConsumer());
		assertFalse(routine.isWordlistRequired());
	}

	@Test
	void createWithoutTheFlagsDefaultsThemToFalse() throws Exception {
		Routine saved = new Routine();
		saved.setId(7L);
		when(routineService.createRoutine(any(), any())).thenReturn(saved);

		mvc.perform(post("/api/routines").contentType(MediaType.APPLICATION_JSON).content(
				"{\"key\":\"fuzz\",\"protocol\":\"HTTP\",\"allowedTargets\":[\"SINGLE_REQUEST\"],\"resultContentType\":\"JSON\"}"))
				.andExpect(status().isCreated());

		ArgumentCaptor<Routine> routine = ArgumentCaptor.forClass(Routine.class);
		verify(routineService).createRoutine(routine.capture(), any());
		assertFalse(routine.getValue().isAvailableAtConsumer());
		assertFalse(routine.getValue().isWordlistRequired());
	}

}
