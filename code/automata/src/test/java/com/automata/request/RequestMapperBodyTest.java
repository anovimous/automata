package com.automata.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.automata.host.Host;
import com.automata.program.Program;
import com.automata.request.common.dto.RequestResponse;
import com.automata.response.Response;
import com.automata.response.ResponseDto;
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
		RequestResponse dto = RequestMapper.toRequestResponse(request(true, 120));

		assertTrue(dto.bodyStoredAsFile());
		assertEquals(120, dto.bodySizeBytes());
	}

	@Test
	void toRequestResponse_inlineBody_flagIsFalse() {
		assertFalse(RequestMapper.toRequestResponse(request(false, 7)).bodyStoredAsFile());
	}

	@Test
	void toResponseDto_exposesTheBodyFileFlagAndSize() {
		Response response = new Response();
		response.setRequest(Request.of(5L));
		response.setBodyStoredAsFile(true);
		response.setBodySizeBytes(99);

		ResponseDto dto = ResponseMapper.toResponseDto(response);

		assertTrue(dto.bodyStoredAsFile());
		assertEquals(99, dto.bodySizeBytes());
	}

}
