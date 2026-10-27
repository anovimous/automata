package com.automata.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.automata.host.common.dto.HostCreationRequest;
import com.automata.host.common.dto.PatchHostRequest;
import com.automata.host.common.enums.Scope;
import com.automata.host.common.exception.HostInUseException;
import com.automata.program.Program;
import com.automata.testsupport.KnownBug;
import com.automata.testsupport.Mvc;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class HostControllerTest {

	@Mock
	HostService hostService;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = Mvc.standalone(new HostController(hostService, Mvc.mapper())).build();
	}

	private static Host host(long id) {
		Host host = new Host();
		host.setId(id);
		host.setHost("api.example.com");
		host.setLevel(1);
		host.setScope(Scope.FQDN);
		host.setHostRateLimit(10);
		Program program = new Program();
		program.setId(3L);
		host.setProgram(program);
		return host;
	}

	@Test
	void getReturnsTheDetailedHost() throws Exception {
		when(hostService.getHostById(1L)).thenReturn(host(1));

		mvc.perform(get("/api/hosts/1")).andExpect(status().isOk()).andExpect(jsonPath("$.host").value("api.example.com"))
				.andExpect(jsonPath("$.programId").value(3)).andExpect(jsonPath("$.hostRateLimit").value(10));
	}

	@Test
	void getOfUnknownHostAnswers404() throws Exception {
		when(hostService.getHostById(9L)).thenThrow(new EntityNotFoundException("Host not found"));

		mvc.perform(get("/api/hosts/9")).andExpect(status().isNotFound());
	}

	@Test
	void listOfAProgramFiltersOnTheQuery() throws Exception {
		when(hostService.getProgramHostsPagedAndFilteredOnQueryString(eq(3L), eq("api"), any()))
				.thenReturn(new PageImpl<>(List.of(host(1))));

		mvc.perform(get("/api/hosts").param("programId", "3").param("query", "api")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].host").value("api.example.com"));
	}

	@Test
	void listOfAnUnknownProgramAnswers404() throws Exception {
		when(hostService.getProgramHostsPagedAndFilteredOnQueryString(eq(9L), any(), any()))
				.thenThrow(new EntityNotFoundException("Program not found"));

		mvc.perform(get("/api/hosts").param("programId", "9")).andExpect(status().isNotFound());
	}

	@Test
	@KnownBug("BUG-014")
	void listWithoutAProgramIdListsEveryHost() throws Exception {
		when(hostService.getHostsPagedAndFilteredOnQueryString(eq(""), any())).thenReturn(new PageImpl<>(List.of(host(1))));

		mvc.perform(get("/api/hosts")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(1));
	}

	@Test
	void createAnswers201WithLocation() throws Exception {
		when(hostService.createHost(any(), any())).thenReturn(host(6));

		mvc.perform(post("/api/hosts").contentType(MediaType.APPLICATION_JSON).content(
				"{\"host\":\"api.example.com\",\"scope\":\"FQDN\",\"hostRateLimit\":10,\"shortRateLimit\":2,\"longRateLimit\":3,\"programId\":3}"))
				.andExpect(status().isCreated()).andExpect(header().string("Location", "/api/hosts/6"));

		ArgumentCaptor<HostCreationRequest> request = ArgumentCaptor.forClass(HostCreationRequest.class);
		ArgumentCaptor<Host> created = ArgumentCaptor.forClass(Host.class);
		verify(hostService).createHost(request.capture(), created.capture());
		assertEquals(3L, request.getValue().programId());
		assertEquals("api.example.com", created.getValue().getHost());
		assertEquals(Scope.FQDN, created.getValue().getScope());
	}

	@Test
	void createRejectedByTheServiceAnswers400() throws Exception {
		when(hostService.createHost(any(), any())).thenThrow(new IllegalArgumentException("Host is of invalid format"));

		mvc.perform(post("/api/hosts").contentType(MediaType.APPLICATION_JSON).content("{\"host\":\"nope\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void patchAnswers204AndPassesTheRequest() throws Exception {
		mvc.perform(patch("/api/hosts/1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"hostRateLimit\":20,\"technologies\":[\"nginx\"]}")).andExpect(status().isNoContent());

		ArgumentCaptor<PatchHostRequest> request = ArgumentCaptor.forClass(PatchHostRequest.class);
		verify(hostService).patchHost(eq(1L), request.capture());
		assertEquals(20, request.getValue().hostRateLimit());
		assertEquals(List.of("nginx"), request.getValue().technologies());
	}

	@Test
	void patchOfAHostInUseAnswers409() throws Exception {
		when(hostService.patchHost(eq(1L), any())).thenThrow(new HostInUseException("in use"));

		mvc.perform(patch("/api/hosts/1").contentType(MediaType.APPLICATION_JSON).content("{\"hostRateLimit\":20}"))
				.andExpect(status().isConflict());
	}

	@Test
	void patchRejectsAnUnknownScopeName() throws Exception {
		mvc.perform(patch("/api/hosts/1").contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"GALAXY\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void deleteAnswers501WhileTheOperationIsNotImplemented() throws Exception {
		org.mockito.Mockito.doThrow(new UnsupportedOperationException("Delete operation is not implemented yet"))
				.when(hostService).deleteHost(1L);

		mvc.perform(delete("/api/hosts/1")).andExpect(status().isNotImplemented());
	}

	@Test
	void deleteAnswers204WhenTheServiceDeletes() throws Exception {
		mvc.perform(delete("/api/hosts/1")).andExpect(status().isNoContent());
	}
}
