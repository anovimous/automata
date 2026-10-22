package com.automata.response;

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
import java.util.List;
import com.automata.storage.StoredBody;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class ResponseControllerBodyTest {

	@Mock
	private ResponseService responseService;

	@InjectMocks
	private ResponseController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void getRawResponse_inlineBody_exposesHeadAndBodySeparately() throws Exception {
		when(responseService.getRawResponse(8L)).thenReturn(new RawHttpMessage("aA==", "Yg==", false));

		mvc.perform(get("/api/responses/8/raw")).andExpect(status().isOk())
				.andExpect(jsonPath("$.rawHeadBase64").value("aA=="))
				.andExpect(jsonPath("$.rawBodyBase64").value("Yg=="))
				.andExpect(jsonPath("$.bodyStoredAsFile").value(false));
	}

	@Test
	void getRawResponse_bodyStoredAsFile_hasNullBodyAndTheFlag() throws Exception {
		when(responseService.getRawResponse(8L)).thenReturn(new RawHttpMessage("aA==", null, true));

		mvc.perform(get("/api/responses/8/raw")).andExpect(status().isOk())
				.andExpect(jsonPath("$.rawBodyBase64").doesNotExist())
				.andExpect(jsonPath("$.bodyStoredAsFile").value(true));
	}

	@Test
	void streamBody_streamsTheFile_asAnAttachment() throws Exception {
		byte[] body = { (byte) 0x89, 'P', 'N', 'G', 0 };
		when(responseService.getResponseBody(8L))
				.thenReturn(new StoredBody(new ByteArrayInputStream(body), "image/png", 5));

		mvc.perform(get("/api/responses/8/body/stream")).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("response-8")))
				.andExpect(header().longValue("Content-Length", 5)).andExpect(content().bytes(body));
	}

	@Test
	void streamBody_withoutABody_is404() throws Exception {
		when(responseService.getResponseBody(8L)).thenThrow(new EntityNotFoundException("Response has no body"));

		mvc.perform(get("/api/responses/8/body/stream")).andExpect(status().isNotFound());
	}

	@Test
	void streamBody_withoutStoredContentType_isOctetStream() throws Exception {
		when(responseService.getResponseBody(8L))
				.thenReturn(new StoredBody(new ByteArrayInputStream(new byte[] { 1 }), null, 1));

		mvc.perform(get("/api/responses/8/body/stream")).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/octet-stream"));
	}

	@Test
	void getResponseMetadata_exposesTheComponents() throws Exception {
		ResponseMetadataDto metadata = ResponseMetadataDto.builder().responseId(8L).requestId(5L).hostId(1L)
				.statusCode(200).headers(List.of(new HeaderEntry("Content-Type", "text/javascript")))
				.body(new BodyMetadata(BodyStorage.FILE, "text/javascript", "JS", 30)).build();
		when(responseService.getResponseMetadata(8L)).thenReturn(metadata);

		mvc.perform(get("/api/responses/8/metadata")).andExpect(status().isOk())
				.andExpect(jsonPath("$.responseId").value(8)).andExpect(jsonPath("$.requestId").value(5))
				.andExpect(jsonPath("$.hostId").value(1)).andExpect(jsonPath("$.statusCode").value(200))
				.andExpect(jsonPath("$.headers[0].name").value("Content-Type"))
				.andExpect(jsonPath("$.body.storage").value("FILE"))
				.andExpect(jsonPath("$.body.contentType").value("text/javascript"));
	}

	@Test
	void getResponseMetadata_unknown_is404() throws Exception {
		when(responseService.getResponseMetadata(8L)).thenThrow(new EntityNotFoundException("Response not found"));

		mvc.perform(get("/api/responses/8/metadata")).andExpect(status().isNotFound());
	}


	@Test
	void streamBody_inlineBody_isServedWithItsContentType() throws Exception {
		byte[] body = "{\"a\":1}".getBytes();
		when(responseService.getResponseBody(8L))
				.thenReturn(new StoredBody(new ByteArrayInputStream(body), "application/json", body.length));

		mvc.perform(get("/api/responses/8/body/stream")).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/json")).andExpect(content().bytes(body));
	}

}
