package com.automata.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.automata.common.dto.BodyMetadata;
import com.automata.common.dto.BodyStorage;
import com.automata.common.dto.HeaderEntry;
import com.automata.common.utils.Base64Utils;
import com.automata.request.common.dto.RequestMetadataResponse;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.common.enums.Source;
import com.automata.response.ResponseContentType;
import com.automata.response.ResponseMetadataDto;

import com.automata.host.Host;
import com.automata.program.Program;
import com.automata.request.common.dto.RequestSummaryResponse;
import com.automata.response.Response;
import com.automata.response.ResponseSummaryDto;
import com.automata.response.ResponseMapper;

class RequestMapperBodyTest {

	private Request request(boolean storedAsFile, long size) {
		Request request = Request.of(5L);
		request.setHost(Host.of(1L));
		Program program = new Program();
		program.setId(2L);
		request.setProgram(program);
		request.setBodyStoredAsFile(storedAsFile);
		request.setBodySizeBytes(size);
		return request;
	}

	@Test
	void toRequestResponse_exposesTheBodyFileFlagAndSize() {
		RequestSummaryResponse dto = RequestMapper.toRequestSummaryResponse(request(true, 120));

		assertTrue(dto.bodyStoredAsFile());
		assertEquals(120, dto.bodySizeBytes());
	}

	@Test
	void toRequestResponse_inlineBody_flagIsFalse() {
		assertFalse(RequestMapper.toRequestSummaryResponse(request(false, 7)).bodyStoredAsFile());
	}

	@Test
	void toResponseDto_exposesTheBodyFileFlagAndSize() {
		Response response = response(true, 99);

		ResponseSummaryDto dto = ResponseMapper.toSummaryDto(response);

		assertTrue(dto.bodyStoredAsFile());
		assertEquals(99, dto.bodySizeBytes());
	}

	@Test
	void toSummaryDto_identifiesTheResponseAndItsHost() {
		ResponseSummaryDto dto = ResponseMapper.toSummaryDto(response(false, 4));

		assertEquals(8L, dto.responseId());
		assertEquals(5L, dto.requestId());
		assertEquals(1L, dto.hostId());
	}

	@Test
	void toRequestSummaryResponse_exposesTheResponseIdWhenThereIsAResponse() {
		Request request = request(false, 7);
		Response response = new Response();
		response.setId(8L);
		request.setResponse(response);

		assertEquals(8L, RequestMapper.toRequestSummaryResponse(request).responseId());
		assertNull(RequestMapper.toRequestSummaryResponse(request(false, 7)).responseId());
	}

	// toMetadataResponse

	private static String head(String text) {
		return Base64Utils.encode(text.getBytes(StandardCharsets.ISO_8859_1));
	}

	@Test
	void toMetadataResponse_inlineJsonBody() {
		Request request = request(false, 7);
		request.setMethod(Method.POST);
		request.setComputatedPath("/api/u/{id}");
		request.setVersion("HTTP/1.1");
		request.setContentType(RequestContentType.JSON);
		request.setSource(Source.BURP);
		Response response = new Response();
		response.setId(8L);
		request.setResponse(response);

		RequestMetadataResponse dto = RequestMapper.toMetadataResponse(request,
				head("POST /api/u/42?x=1 HTTP/1.1\r\nHost: a\r\nContent-Type: application/json\r\n\r\n"));

		assertEquals(5L, dto.requestId());
		assertEquals(1L, dto.hostId());
		assertEquals(2L, dto.programId());
		assertNull(dto.tenantId());
		assertEquals(8L, dto.responseId());
		assertEquals(Source.BURP, dto.source());
		assertEquals(Method.POST, dto.line().method());
		assertEquals("/api/u/{id}", dto.line().computatedPath());
		assertEquals("/api/u/42?x=1", dto.line().target());
		assertEquals(List.of(new HeaderEntry("Host", "a"), new HeaderEntry("Content-Type", "application/json")),
				dto.headers());
		assertEquals(new BodyMetadata(BodyStorage.INLINE, "application/json", "JSON", 7), dto.body());
	}

	@Test
	void toMetadataResponse_multipartBodyStoredAsFile() {
		Request request = request(true, 500);
		request.setContentType(RequestContentType.MULTIPART);

		RequestMetadataResponse dto = RequestMapper.toMetadataResponse(request,
				head("POST /up HTTP/1.1\r\nContent-Type: multipart/form-data; boundary=x\r\n\r\n"));

		assertEquals(new BodyMetadata(BodyStorage.FILE, "multipart/form-data; boundary=x", "MULTIPART", 500),
				dto.body());
	}

	@Test
	void toMetadataResponse_noBody_hasNoStorageAndNoContentType() {
		RequestMetadataResponse dto = RequestMapper.toMetadataResponse(request(false, 0),
				head("GET /a HTTP/1.1\r\nHost: a\r\n\r\n"));

		assertEquals(new BodyMetadata(BodyStorage.NONE, null, null, 0), dto.body());
		assertNull(dto.responseId());
	}

	@Test
	void toMetadataResponse_tenantIsExposedWhenTheRequestHasOne() {
		Request request = request(false, 0);
		com.automata.tenant.Tenant tenant = new com.automata.tenant.Tenant();
		tenant.setId(4L);
		request.setTenant(tenant);

		assertEquals(4L, RequestMapper.toMetadataResponse(request, head("GET /a HTTP/1.1\r\n\r\n")).tenantId());
	}

	// ResponseMapper.toMetadataDto

	private Response response(boolean storedAsFile, long size) {
		Response response = new Response();
		response.setId(8L);
		response.setStatusCode(200);
		response.setBodyStoredAsFile(storedAsFile);
		response.setBodySizeBytes(size);
		response.setRequest(request(false, 0));
		return response;
	}

	@Test
	void toMetadataDto_fileBody() {
		Response response = response(true, 30);
		response.setContentType(ResponseContentType.JS);

		ResponseMetadataDto dto = ResponseMapper.toMetadataDto(response,
				head("HTTP/1.1 200 OK\r\nContent-Type: text/javascript\r\nServer: x\r\n\r\n"));

		assertEquals(8L, dto.responseId());
		assertEquals(5L, dto.requestId());
		assertEquals(1L, dto.hostId());
		assertEquals(200, dto.statusCode());
		assertEquals(List.of(new HeaderEntry("Content-Type", "text/javascript"), new HeaderEntry("Server", "x")),
				dto.headers());
		assertEquals(new BodyMetadata(BodyStorage.FILE, "text/javascript", "JS", 30), dto.body());
	}

	@Test
	void toMetadataDto_inlineJsonBody() {
		Response response = response(false, 12);
		response.setContentType(ResponseContentType.JSON);

		ResponseMetadataDto dto = ResponseMapper.toMetadataDto(response,
				head("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n"));

		assertEquals(new BodyMetadata(BodyStorage.INLINE, "application/json", "JSON", 12), dto.body());
	}

	@Test
	void toMetadataDto_emptyBody_hasNoStorage() {
		ResponseMetadataDto dto = ResponseMapper.toMetadataDto(response(false, 0), head("HTTP/1.1 204 No Content\r\n\r\n"));

		assertEquals(new BodyMetadata(BodyStorage.NONE, null, null, 0), dto.body());
		assertTrue(dto.headers().isEmpty());
	}

}
