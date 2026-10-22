package com.automata.agentcontext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.automata.GlobalExceptionHandler;
import com.automata.agentcontext.common.dto.ResolvedTarget;
import com.automata.agentcontext.common.enums.AgentContextItemType;
import com.automata.common.dto.BodyMetadata;
import com.automata.common.dto.BodyStorage;
import com.automata.common.dto.RawHttpMessage;
import com.automata.request.RequestService;
import com.automata.request.common.dto.RequestMetadataResponse;
import com.automata.response.Response;
import com.automata.response.ResponseMetadataDto;
import com.automata.response.ResponseService;
import com.automata.storage.StoredBody;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class AgentContextReadControllerTest {

	private static final UUID UUID_OF_CONTEXT = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static final String BASE = "/api/agent-contexts/uuid/" + UUID_OF_CONTEXT;

	@Mock
	private AgentContextService contextService;
	@Mock
	private RequestService requestService;
	@Mock
	private ResponseService responseService;

	@InjectMocks
	private AgentContextReadController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	private void requestNotInContext(Long requestId) {
		doThrow(new EntityNotFoundException("Request not found")).when(contextService)
				.requireRequestInContext(UUID_OF_CONTEXT, requestId);
	}

	private Response responseOf(Long id) {
		Response response = new Response();
		response.setId(id);
		return response;
	}

	// resolved-requests

	@Test
	void resolvedRequests_listsTheTargetsOfTheContext() throws Exception {
		when(contextService.getContextByUuid(UUID_OF_CONTEXT)).thenReturn(AgentContext.builder().id(1L).build());
		when(contextService.resolveContextTargets(1L)).thenReturn(List.of(ResolvedTarget.builder().requestId(7L)
				.automataJobEligible(true).sourceItemType(AgentContextItemType.REQUEST).build()));

		mvc.perform(get(BASE + "/resolved-requests")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].requestId").value(7));
	}

	@Test
	void resolvedRequests_unknownContext_is404() throws Exception {
		when(contextService.getContextByUuid(UUID_OF_CONTEXT))
				.thenThrow(new EntityNotFoundException("AgentContext not found"));

		mvc.perform(get(BASE + "/resolved-requests")).andExpect(status().isNotFound());
	}

	// request routes

	@Test
	void requestMetadata_inContext_isServed() throws Exception {
		when(requestService.getRequestMetadata(7L)).thenReturn(RequestMetadataResponse.builder().requestId(7L)
				.body(new BodyMetadata(BodyStorage.NONE, null, null, 0)).build());

		mvc.perform(get(BASE + "/requests/7/metadata")).andExpect(status().isOk())
				.andExpect(jsonPath("$.requestId").value(7));
	}

	@Test
	void requestMetadata_outsideTheContext_is404_andReadsNothing() throws Exception {
		requestNotInContext(7L);

		mvc.perform(get(BASE + "/requests/7/metadata")).andExpect(status().isNotFound());

		verifyNoInteractions(requestService);
	}

	@Test
	void rawRequest_inContext_carriesTheResponseId() throws Exception {
		when(requestService.getRawRequest(7L)).thenReturn(new RawHttpMessage("aA==", "Yg==", false));
		when(responseService.getResponseOfRequest(7L)).thenReturn(responseOf(8L));

		mvc.perform(get(BASE + "/requests/7/raw")).andExpect(status().isOk())
				.andExpect(jsonPath("$.rawHeadBase64").value("aA==")).andExpect(jsonPath("$.responseId").value(8));
	}

	@Test
	void rawRequest_withoutAResponse_hasNoResponseId() throws Exception {
		when(requestService.getRawRequest(7L)).thenReturn(new RawHttpMessage("aA==", null, true));
		when(responseService.getResponseOfRequest(7L)).thenReturn(null);

		mvc.perform(get(BASE + "/requests/7/raw")).andExpect(status().isOk())
				.andExpect(jsonPath("$.responseId").doesNotExist());
	}

	@Test
	void rawRequest_outsideTheContext_is404_andReadsNothing() throws Exception {
		requestNotInContext(7L);

		mvc.perform(get(BASE + "/requests/7/raw")).andExpect(status().isNotFound());

		verifyNoInteractions(requestService, responseService);
	}

	@Test
	void requestBody_inContext_isStreamed() throws Exception {
		byte[] body = { 1, 2, 3 };
		when(requestService.getRequestBody(7L))
				.thenReturn(new StoredBody(new ByteArrayInputStream(body), "image/png", 3));

		mvc.perform(get(BASE + "/requests/7/body/stream")).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png")).andExpect(content().bytes(body));
	}

	@Test
	void requestBody_outsideTheContext_is404_andReadsNothing() throws Exception {
		requestNotInContext(7L);

		mvc.perform(get(BASE + "/requests/7/body/stream")).andExpect(status().isNotFound());

		verifyNoInteractions(requestService);
	}

	// response routes

	@Test
	void responseMetadata_isReadThroughTheRequest() throws Exception {
		when(responseService.getResponseOfRequest(7L)).thenReturn(responseOf(8L));
		when(responseService.getResponseMetadata(8L)).thenReturn(ResponseMetadataDto.builder().responseId(8L)
				.requestId(7L).statusCode(200).body(new BodyMetadata(BodyStorage.NONE, null, null, 0)).build());

		mvc.perform(get(BASE + "/requests/7/response/metadata")).andExpect(status().isOk())
				.andExpect(jsonPath("$.responseId").value(8)).andExpect(jsonPath("$.statusCode").value(200));
	}

	@Test
	void responseMetadata_ofARequestOutsideTheContext_is404_andReadsNothing() throws Exception {
		requestNotInContext(7L);

		mvc.perform(get(BASE + "/requests/7/response/metadata")).andExpect(status().isNotFound());

		verifyNoInteractions(responseService);
	}

	@Test
	void responseMetadata_requestWithoutAResponse_is404() throws Exception {
		when(responseService.getResponseOfRequest(7L)).thenReturn(null);

		mvc.perform(get(BASE + "/requests/7/response/metadata")).andExpect(status().isNotFound());

		verify(responseService, never()).getResponseMetadata(any());
	}

	@Test
	void rawResponse_isReadThroughTheRequest() throws Exception {
		when(responseService.getResponseOfRequest(7L)).thenReturn(responseOf(8L));
		when(responseService.getRawResponse(8L)).thenReturn(new RawHttpMessage("aA==", null, true));

		mvc.perform(get(BASE + "/requests/7/response/raw")).andExpect(status().isOk())
				.andExpect(jsonPath("$.rawHeadBase64").value("aA=="))
				.andExpect(jsonPath("$.rawBodyBase64").doesNotExist())
				.andExpect(jsonPath("$.bodyStoredAsFile").value(true));
	}

	@Test
	void rawResponse_ofARequestOutsideTheContext_is404_andReadsNothing() throws Exception {
		requestNotInContext(7L);

		mvc.perform(get(BASE + "/requests/7/response/raw")).andExpect(status().isNotFound());

		verifyNoInteractions(responseService);
	}

	@Test
	void rawResponse_requestWithoutAResponse_is404() throws Exception {
		when(responseService.getResponseOfRequest(7L)).thenReturn(null);

		mvc.perform(get(BASE + "/requests/7/response/raw")).andExpect(status().isNotFound());
	}

	@Test
	void responseBody_isStreamedThroughTheRequest() throws Exception {
		byte[] body = "alert(1)".getBytes();
		when(responseService.getResponseOfRequest(7L)).thenReturn(responseOf(8L));
		when(responseService.getResponseBody(8L))
				.thenReturn(new StoredBody(new ByteArrayInputStream(body), "text/javascript", body.length));

		mvc.perform(get(BASE + "/requests/7/response/body/stream")).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/javascript")).andExpect(content().bytes(body));
	}

	@Test
	void responseBody_ofARequestOutsideTheContext_is404_andReadsNothing() throws Exception {
		requestNotInContext(7L);

		mvc.perform(get(BASE + "/requests/7/response/body/stream")).andExpect(status().isNotFound());

		verifyNoInteractions(responseService);
	}

	@Test
	void responseBody_requestWithoutAResponse_is404() throws Exception {
		when(responseService.getResponseOfRequest(7L)).thenReturn(null);

		mvc.perform(get(BASE + "/requests/7/response/body/stream")).andExpect(status().isNotFound());
	}

	// tenants

	@Test
	void tenants_listsTheTenantsWithTheirRequestsAndAuthentication() throws Exception {
		when(contextService.getContextTenants(UUID_OF_CONTEXT)).thenReturn(List.of(
				com.automata.agentcontext.common.dto.AgentContextTenantResponse.builder().tenantId(4L).name("alice")
						.email("a@a.com").requestIds(List.of(10L, 12L)).build()));

		mvc.perform(get(BASE + "/tenants")).andExpect(status().isOk()).andExpect(jsonPath("$[0].tenantId").value(4))
				.andExpect(jsonPath("$[0].name").value("alice"))
				.andExpect(jsonPath("$[0].requestIds[1]").value(12));
	}

	@Test
	void tenants_unknownContext_is404() throws Exception {
		when(contextService.getContextTenants(UUID_OF_CONTEXT))
				.thenThrow(new EntityNotFoundException("AgentContext not found"));

		mvc.perform(get(BASE + "/tenants")).andExpect(status().isNotFound());
	}

	// host

	@Test
	void host_isTheHostOfTheContext() throws Exception {
		com.automata.host.Host host = com.automata.host.Host.of(3L);
		host.setHost("api.example.com");
		host.setDescription("main api");
		host.setTechnologies(List.of("nginx", "spring"));
		host.setNotes("rate limited");
		com.automata.program.Program program = new com.automata.program.Program();
		program.setId(2L);
		host.setProgram(program);
		when(contextService.getContextHost(UUID_OF_CONTEXT)).thenReturn(host);

		mvc.perform(get(BASE + "/host")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(3))
				.andExpect(jsonPath("$.host").value("api.example.com"))
				.andExpect(jsonPath("$.programId").value(2)).andExpect(jsonPath("$.description").value("main api"))
				.andExpect(jsonPath("$.technologies[1]").value("spring"))
				.andExpect(jsonPath("$.notes").value("rate limited"));
	}

	@Test
	void host_unknownContext_is404() throws Exception {
		when(contextService.getContextHost(UUID_OF_CONTEXT))
				.thenThrow(new EntityNotFoundException("AgentContext not found"));

		mvc.perform(get(BASE + "/host")).andExpect(status().isNotFound());
	}

}
