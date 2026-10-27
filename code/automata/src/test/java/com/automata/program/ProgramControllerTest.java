package com.automata.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.automata.testsupport.Mvc;
import com.automata.program.common.dto.PatchProgramRequest;
import com.automata.program.common.enums.Platform;
import com.automata.testsupport.KnownBug;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProgramControllerTest {

	@Mock
	ProgramService programService;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = Mvc.standalone(new ProgramController(programService)).build();
	}

	private Program program(long id, String name) {
		Program program = new Program();
		program.setId(id);
		program.setName(name);
		return program;
	}

	@Test
	void getReturnsTheDetailedProgram() throws Exception {
		Program program = program(3, "Acme");
		program.setPlatform(Platform.HACKERONE);
		when(programService.getProgramWithVulns(3L)).thenReturn(program);

		mvc.perform(get("/api/programs/3")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(3))
				.andExpect(jsonPath("$.name").value("Acme")).andExpect(jsonPath("$.platform").value("HACKERONE"))
				.andExpect(jsonPath("$.outOfScopeVulnIds").isEmpty());
	}

	@Test
	void getOfAnUnknownProgramAnswers404() throws Exception {
		when(programService.getProgramWithVulns(9L)).thenThrow(new EntityNotFoundException("Program not found"));

		mvc.perform(get("/api/programs/9")).andExpect(status().isNotFound());
	}

	@Test
	void listWrapsTheSummariesAndPassesTheQuery() throws Exception {
		when(programService.getProgramsPagedAndFilteredOnQueryString(eq("ac"), any()))
				.thenReturn(new PageImpl<>(List.of(program(1, "Acme"))));

		mvc.perform(get("/api/programs").param("query", "ac")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].name").value("Acme"));
	}

	@Test
	void listDefaultsToAnEmptyQueryAndNewestFirst() throws Exception {
		when(programService.getProgramsPagedAndFilteredOnQueryString(any(), any())).thenReturn(new PageImpl<>(List.of()));

		mvc.perform(get("/api/programs")).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(programService).getProgramsPagedAndFilteredOnQueryString(eq(""), pageable.capture());
		assertEquals(10, pageable.getValue().getPageSize());
		assertEquals("insertionDate: DESC", pageable.getValue().getSort().toString());
	}

	@Test
	void createAnswers201WithTheLocationOfTheNewProgram() throws Exception {
		when(programService.createProgram(any())).thenReturn(program(12, "Acme"));

		mvc.perform(post("/api/programs").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Acme\",\"programRateLimit\":10}")).andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/programs/12"));
	}

	@Test
	void patchAnswers204AndHandsTheRequestToTheService() throws Exception {
		mvc.perform(patch("/api/programs/3").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"New\",\"setLinkNull\":true,\"outOfScopeVulnIds\":[1,2]}"))
				.andExpect(status().isNoContent());

		ArgumentCaptor<PatchProgramRequest> request = ArgumentCaptor.forClass(PatchProgramRequest.class);
		verify(programService).patchProgram(eq(3L), request.capture());
		assertEquals("New", request.getValue().name());
		assertEquals(true, request.getValue().setLinkNull());
		assertEquals(Set.of(1L, 2L), request.getValue().outOfScopeVulnIds());
	}

	@Test
	void patchOfAnUnknownProgramAnswers404() throws Exception {
		when(programService.patchProgram(eq(9L), any())).thenThrow(new EntityNotFoundException("Program not found"));

		mvc.perform(patch("/api/programs/9").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void patchWithAnUnknownPlatformAnswers400() throws Exception {
		mvc.perform(patch("/api/programs/3").contentType(MediaType.APPLICATION_JSON).content("{\"platform\":\"NOPE\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void deleteAnswers204WhenTheServiceDeletes() throws Exception {
		mvc.perform(delete("/api/programs/3")).andExpect(status().isNoContent());
		verify(programService).deleteProgram(3L);
	}

	@Test
	void deleteAnswers501WhileTheOperationIsNotImplemented() throws Exception {
		doThrow(new UnsupportedOperationException("Delete operation is not implemented yet")).when(programService)
				.deleteProgram(3L);

		mvc.perform(delete("/api/programs/3")).andExpect(status().isNotImplemented());
	}

	// ---- BUG-004: the web layer answers 500 for caller mistakes ----

	@Test
	@KnownBug("BUG-004")
	void aNonNumericIdAnswers400() throws Exception {
		mvc.perform(get("/api/programs/abc")).andExpect(status().isBadRequest());
	}
}
