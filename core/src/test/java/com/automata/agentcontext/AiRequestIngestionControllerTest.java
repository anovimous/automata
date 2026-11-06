package com.automata.agentcontext;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.automata.GlobalExceptionHandler;
import com.automata.agentcontext.common.dto.AiRequestAdditionDto;
import com.automata.host.Host;
import com.automata.program.Program;
import com.automata.request.Request;
import com.automata.request.common.enums.Source;
import com.automata.response.Response;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class AiRequestIngestionControllerTest {

	private static final String URL = "/api/external/ai/requests";

	@Mock
	private AiRequestIngestionService ingestionService;

	@InjectMocks
	private AiRequestIngestionController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	private Request created() {
		Request request = Request.of(9L);
		request.setHost(Host.of(3L));
		Program program = new Program();
		program.setId(2L);
		request.setProgram(program);
		request.setSource(Source.AI);
		Response response = new Response();
		response.setId(12L);
		request.setResponse(response);
		return request;
	}

	@Test
	void addRawRequest_is201WithTheIdsAndNoLocation() throws Exception {
		when(ingestionService.addRawRequest(any())).thenReturn(created());

		mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
				.content("{\"requestBase64\":\"cmVx\",\"host\":\"a.com\",\"base64Response\":\"cmVzcA==\"}"))
				.andExpect(status().isCreated()).andExpect(header().doesNotExist("Location"))
				.andExpect(jsonPath("$.requestId").value(9)).andExpect(jsonPath("$.responseId").value(12))
				.andExpect(jsonPath("$.source").value("AI"));
	}

	@Test
	void addRawRequest_passesTheBodyFieldsOn() throws Exception {
		when(ingestionService.addRawRequest(any())).thenReturn(created());
		UUID uuid = UUID.fromString("11111111-1111-1111-1111-111111111111");

		mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(
				"{\"requestBase64\":\"cmVx\",\"host\":\"a.com\",\"tenantId\":4,\"agentContextUuid\":\"" + uuid + "\"}"))
				.andExpect(status().isCreated());

		ArgumentCaptor<AiRequestAdditionDto> captor = forClass(AiRequestAdditionDto.class);
		verify(ingestionService).addRawRequest(captor.capture());
		assertEquals("cmVx", captor.getValue().requestBase64());
		assertEquals("a.com", captor.getValue().host());
		assertNull(captor.getValue().base64Response());
		assertEquals(4L, captor.getValue().tenantId());
		assertEquals(uuid, captor.getValue().agentContextUuid());
	}

	@Test
	void addRawRequest_unknownHost_is404() throws Exception {
		when(ingestionService.addRawRequest(any())).thenThrow(new EntityNotFoundException("Host not found"));

		mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
				.content("{\"requestBase64\":\"cmVx\",\"host\":\"nope.com\"}")).andExpect(status().isNotFound());
	}

	@Test
	void addRawRequest_rejectedTenant_is400() throws Exception {
		when(ingestionService.addRawRequest(any()))
				.thenThrow(new IllegalArgumentException("agentContextUuid is required when a tenantId is given"));

		mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
				.content("{\"requestBase64\":\"cmVx\",\"host\":\"a.com\",\"tenantId\":4}"))
				.andExpect(status().isBadRequest());
	}

}
