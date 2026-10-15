package com.automata.request;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.automata.GlobalExceptionHandler;
import com.automata.common.dto.RawHttpMessage;
import com.automata.request.equalityset.RequestEqualityService;
import com.automata.response.Response;
import com.automata.response.ResponseService;
import com.automata.storage.StoredBody;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class RequestControllerBodyTest {

	@Mock
	private RequestService requestService;
	@Mock
	private RequestEqualityService requestEqualityService;
	@Mock
	private ResponseService responseService;

	@InjectMocks
	private RequestController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	// GET /{id}

	@Test
	void getRequest_inlineBody_exposesHeadAndBodySeparately() throws Exception {
		when(requestService.getRawRequest(5L)).thenReturn(new RawHttpMessage("aGVhZA==", "Ym9keQ==", false));
		Response response = new Response();
		response.setId(8L);
		when(responseService.getResponseOfRequest(5L)).thenReturn(response);

		mvc.perform(get("/api/requests/5")).andExpect(status().isOk())
				.andExpect(jsonPath("$.rawHeadBase64").value("aGVhZA=="))
				.andExpect(jsonPath("$.rawBodyBase64").value("Ym9keQ=="))
				.andExpect(jsonPath("$.bodyStoredAsFile").value(false)).andExpect(jsonPath("$.responseId").value(8));
	}

	@Test
	void getRequest_bodyStoredAsFile_hasNullBodyAndTheFlag() throws Exception {
		when(requestService.getRawRequest(5L)).thenReturn(new RawHttpMessage("aGVhZA==", null, true));

		mvc.perform(get("/api/requests/5")).andExpect(status().isOk())
				.andExpect(jsonPath("$.rawHeadBase64").value("aGVhZA=="))
				.andExpect(jsonPath("$.rawBodyBase64").doesNotExist())
				.andExpect(jsonPath("$.bodyStoredAsFile").value(true)).andExpect(jsonPath("$.responseId").doesNotExist());
	}

	@Test
	void getRequest_unknown_is404() throws Exception {
		when(requestService.getRawRequest(5L)).thenThrow(new EntityNotFoundException("Request not found"));

		mvc.perform(get("/api/requests/5")).andExpect(status().isNotFound());
	}

	// GET /{id}/body

	@Test
	void downloadBody_streamsTheFile_asAnAttachment() throws Exception {
		byte[] body = { 1, 2, 3, (byte) 0xFF };
		when(requestService.getRequestBodyFile(5L))
				.thenReturn(new StoredBody(new ByteArrayInputStream(body), "multipart/form-data; boundary=x", 4));

		mvc.perform(get("/api/requests/5/body")).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "multipart/form-data;boundary=x"))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("request-5")))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
				.andExpect(content().bytes(body));
	}

	@Test
	void downloadBody_withoutAFile_is404() throws Exception {
		when(requestService.getRequestBodyFile(5L)).thenThrow(new EntityNotFoundException("Request has no body file"));

		mvc.perform(get("/api/requests/5/body")).andExpect(status().isNotFound());
	}

}
