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
import com.automata.common.dto.BodyMetadata;
import com.automata.common.dto.BodyStorage;
import com.automata.common.dto.HeaderEntry;
import com.automata.common.dto.RawHttpMessage;
import com.automata.request.common.dto.RequestLine;
import com.automata.request.common.dto.RequestMetadataResponse;
import com.automata.request.common.enums.Method;
import java.util.List;
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

	// GET /{id}/raw

	@Test
	void getRawRequest_inlineBody_exposesHeadAndBodySeparately() throws Exception {
		when(requestService.getRawRequest(5L)).thenReturn(new RawHttpMessage("aGVhZA==", "Ym9keQ==", false));
		Response response = new Response();
		response.setId(8L);
		when(responseService.getResponseOfRequest(5L)).thenReturn(response);

		mvc.perform(get("/api/requests/5/raw")).andExpect(status().isOk())
				.andExpect(jsonPath("$.rawHeadBase64").value("aGVhZA=="))
				.andExpect(jsonPath("$.rawBodyBase64").value("Ym9keQ=="))
				.andExpect(jsonPath("$.bodyStoredAsFile").value(false)).andExpect(jsonPath("$.responseId").value(8));
	}

	@Test
	void getRawRequest_bodyStoredAsFile_hasNullBodyAndTheFlag() throws Exception {
		when(requestService.getRawRequest(5L)).thenReturn(new RawHttpMessage("aGVhZA==", null, true));

		mvc.perform(get("/api/requests/5/raw")).andExpect(status().isOk())
				.andExpect(jsonPath("$.rawHeadBase64").value("aGVhZA=="))
				.andExpect(jsonPath("$.rawBodyBase64").doesNotExist())
				.andExpect(jsonPath("$.bodyStoredAsFile").value(true)).andExpect(jsonPath("$.responseId").doesNotExist());
	}

	@Test
	void getRawRequest_unknown_is404() throws Exception {
		when(requestService.getRawRequest(5L)).thenThrow(new EntityNotFoundException("Request not found"));

		mvc.perform(get("/api/requests/5/raw")).andExpect(status().isNotFound());
	}

	// GET /{id}/body/stream

	@Test
	void streamBody_streamsTheFile_asAnAttachment() throws Exception {
		byte[] body = { 1, 2, 3, (byte) 0xFF };
		when(requestService.getRequestBody(5L))
				.thenReturn(new StoredBody(new ByteArrayInputStream(body), "multipart/form-data; boundary=x", 4));

		mvc.perform(get("/api/requests/5/body/stream")).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "multipart/form-data;boundary=x"))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("request-5")))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
				.andExpect(content().bytes(body));
	}

	@Test
	void streamBody_withoutABody_is404() throws Exception {
		when(requestService.getRequestBody(5L)).thenThrow(new EntityNotFoundException("Request has no body"));

		mvc.perform(get("/api/requests/5/body/stream")).andExpect(status().isNotFound());
	}

	// GET /{id}/metadata

	@Test
	void getRequestMetadata_exposesTheComponents() throws Exception {
		RequestMetadataResponse metadata = RequestMetadataResponse.builder().requestId(5L).hostId(1L).programId(2L)
				.responseId(8L).line(new RequestLine(Method.POST, "/api/u", null, "HTTP/1.1", "/api/u?x=1"))
				.headers(List.of(new HeaderEntry("Content-Type", "application/json")))
				.body(new BodyMetadata(BodyStorage.INLINE, "application/json", "JSON", 7)).build();
		when(requestService.getRequestMetadata(5L)).thenReturn(metadata);

		mvc.perform(get("/api/requests/5/metadata")).andExpect(status().isOk())
				.andExpect(jsonPath("$.requestId").value(5)).andExpect(jsonPath("$.responseId").value(8))
				.andExpect(jsonPath("$.line.target").value("/api/u?x=1"))
				.andExpect(jsonPath("$.headers[0].name").value("Content-Type"))
				.andExpect(jsonPath("$.headers[0].value").value("application/json"))
				.andExpect(jsonPath("$.body.storage").value("INLINE"))
				.andExpect(jsonPath("$.body.mimeFamily").value("JSON")).andExpect(jsonPath("$.body.sizeBytes").value(7));
	}

	@Test
	void getRequestMetadata_unknown_is404() throws Exception {
		when(requestService.getRequestMetadata(5L)).thenThrow(new EntityNotFoundException("Request not found"));

		mvc.perform(get("/api/requests/5/metadata")).andExpect(status().isNotFound());
	}


	@Test
	void streamBody_inlineBody_isServedWithItsContentType() throws Exception {
		byte[] body = "{\"a\":1}".getBytes();
		when(requestService.getRequestBody(5L))
				.thenReturn(new StoredBody(new ByteArrayInputStream(body), "application/json", body.length));

		mvc.perform(get("/api/requests/5/body/stream")).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/json")).andExpect(content().bytes(body));
	}

}
