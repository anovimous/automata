package com.automata.agentcontext;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.automata.GlobalExceptionHandler;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class AgentContextControllerTenantsTest {

	@Mock
	private AgentContextService contextService;

	@InjectMocks
	private AgentContextController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void addTenant_is204AndPassesTheIds() throws Exception {
		mvc.perform(post("/api/agent-contexts/1/tenants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"tenantId\":4}")).andExpect(status().isNoContent());

		verify(contextService).addTenant(1L, 4L);
	}

	@Test
	void addTenant_unknownTenant_is404() throws Exception {
		doThrow(new EntityNotFoundException("Tenant not found")).when(contextService).addTenant(1L, 4L);

		mvc.perform(post("/api/agent-contexts/1/tenants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"tenantId\":4}")).andExpect(status().isNotFound());
	}

	@Test
	void removeTenant_is204() throws Exception {
		mvc.perform(delete("/api/agent-contexts/1/tenants/4")).andExpect(status().isNoContent());

		verify(contextService).removeTenant(1L, 4L);
	}

	@Test
	void removeTenant_notInTheContext_is404() throws Exception {
		doThrow(new EntityNotFoundException("Tenant is not in this context")).when(contextService).removeTenant(1L, 4L);

		mvc.perform(delete("/api/agent-contexts/1/tenants/4")).andExpect(status().isNotFound());
	}

}
