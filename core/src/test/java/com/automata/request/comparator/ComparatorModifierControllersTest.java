package com.automata.request.comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.automata.request.comparator.common.dto.ComparatorPatchRequest;
import com.automata.request.comparator.common.dto.ModifierPatchRequest;
import com.automata.request.comparator.common.enums.Schema;
import com.automata.request.comparator.hash.HashTarget;
import com.automata.testsupport.Mvc;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class ComparatorModifierControllersTest {

	@Mock
	ComparatorService comparatorService;

	@Mock
	ModifierService modifierService;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = Mvc.standalone(new ComparatorController(comparatorService), new ModifierController(modifierService)).build();
	}

	private static Comparator comparator(long id) {
		return Comparator.builder().id(id).name("rest").schema(Schema.REST).build();
	}

	private static Modifier modifier(long id) {
		return Modifier.builder().id(id).target(HashTarget.PATH).priority(1L).description("d").isAvailable(true).build();
	}

	// ---- comparators ----

	@Test
	void getComparator() throws Exception {
		when(comparatorService.getComparatorById(1L)).thenReturn(comparator(1));

		mvc.perform(get("/api/comparators/1")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("rest"))
				.andExpect(jsonPath("$.schema").value("REST"));
	}

	@Test
	void getUnknownComparatorAnswers404() throws Exception {
		when(comparatorService.getComparatorById(9L)).thenThrow(new EntityNotFoundException("Comparator not found"));

		mvc.perform(get("/api/comparators/9")).andExpect(status().isNotFound());
	}

	@Test
	void listComparatorsWithFilters() throws Exception {
		when(comparatorService.getComparatorsPagedAndFiltered(eq("re"), eq(Schema.GRAPHQL), any()))
				.thenReturn(new PageImpl<>(List.of(comparator(1))));

		mvc.perform(get("/api/comparators").param("query", "re").param("schema", "GRAPHQL")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(1));
	}

	@Test
	void createComparatorPassesTheModifierIds() throws Exception {
		when(comparatorService.createComparator(any(), any())).thenReturn(comparator(4));

		mvc.perform(post("/api/comparators").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"c\",\"schema\":\"REST\",\"modifierIds\":[1,2]}")).andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/comparators/4"));

		verify(comparatorService).createComparator(any(), eq(Set.of(1L, 2L)));
	}

	@Test
	void patchComparatorAnswers204() throws Exception {
		mvc.perform(patch("/api/comparators/1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"n\",\"setSchemaNull\":true}")).andExpect(status().isNoContent());

		ArgumentCaptor<ComparatorPatchRequest> request = ArgumentCaptor.forClass(ComparatorPatchRequest.class);
		verify(comparatorService).patchComparator(eq(1L), request.capture());
		assertEquals("n", request.getValue().name().orElseThrow());
		assertTrue(request.getValue().schema().isEmpty());
		assertTrue(request.getValue().setSchemaNull());
	}

	@Test
	void deleteComparatorAnswers204() throws Exception {
		mvc.perform(delete("/api/comparators/1")).andExpect(status().isNoContent());

		verify(comparatorService).deleteComparator(1L);
	}

	// ---- modifiers ----

	@Test
	void getModifier() throws Exception {
		when(modifierService.getModifierById(1L)).thenReturn(modifier(1));

		mvc.perform(get("/api/modifiers/1")).andExpect(status().isOk()).andExpect(jsonPath("$.target").value("PATH"))
				.andExpect(jsonPath("$.isAvailable").value(true));
	}

	@Test
	void listModifiersByTarget() throws Exception {
		when(modifierService.getModifiersPagedAndFilteredOnHashTargetAndSchema(eq(HashTarget.PATH), eq(null), any()))
				.thenReturn(new PageImpl<>(List.of(modifier(1))));

		mvc.perform(get("/api/modifiers").param("target", "PATH")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].target").value("PATH"));
	}

	@Test
	void createModifierAnswers201() throws Exception {
		when(modifierService.createModifier(any())).thenReturn(modifier(6));

		mvc.perform(post("/api/modifiers").contentType(MediaType.APPLICATION_JSON)
				.content("{\"target\":\"PATH\",\"description\":\"d\",\"priority\":1,\"isAvailable\":true}"))
				.andExpect(status().isCreated()).andExpect(header().string("Location", "/api/modifiers/6"));

		ArgumentCaptor<Modifier> created = ArgumentCaptor.forClass(Modifier.class);
		verify(modifierService).createModifier(created.capture());
		assertEquals(HashTarget.PATH, created.getValue().getTarget());
		assertTrue(created.getValue().isAvailable());
	}

	@Test
	void createModifierWithAnUnknownTargetAnswers400() throws Exception {
		mvc.perform(post("/api/modifiers").contentType(MediaType.APPLICATION_JSON).content("{\"target\":\"HEADERS\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void patchModifierAnswers204() throws Exception {
		mvc.perform(patch("/api/modifiers/1").contentType(MediaType.APPLICATION_JSON).content("{\"isAvailable\":false}"))
				.andExpect(status().isNoContent());

		ArgumentCaptor<ModifierPatchRequest> request = ArgumentCaptor.forClass(ModifierPatchRequest.class);
		verify(modifierService).patchModifier(eq(1L), request.capture());
		assertEquals(false, request.getValue().isAvailable().orElseThrow());
	}

	@Test
	void deleteModifierAnswers204() throws Exception {
		mvc.perform(delete("/api/modifiers/1")).andExpect(status().isNoContent());

		verify(modifierService).deleteModifier(1L);
	}
}
