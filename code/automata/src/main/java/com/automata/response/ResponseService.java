package com.automata.response;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.common.utils.Base64Utils;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.body.BodyProperty;
import com.automata.request.body.BodyPropertyRepository;
import com.automata.request.body.BodyPropertyService;
import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.header.HeaderService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResponseService {

	private final ResponseRepository responseRepo;

	private final RequestRepository requestRepo;

	private final BodyPropertyRepository bodyPropertyRepo;

	private final HeaderService headerService;

	private final BodyPropertyService bodyService;

	public Response getResponseById(Long responseId) {

		return responseRepo.findById(responseId).orElseThrow(() -> new EntityNotFoundException("Response not found"));

	}

	@Transactional(readOnly = true)
	public String getRawResponse(Long responseId) {

		Response response = responseRepo.findById(responseId)
				.orElseThrow(() -> new EntityNotFoundException("Response not found"));

		ResponseInternalDto dto = ResponseInternalDto.builder().id(response.getId())
				.statusCode(response.getStatusCode()).contentType(response.getContentType())
				.contentLength(response.getContentLength()).build();

		List<Long> ids = List.of(response.getId());

		List<BodyPropertyInternalDto> bodyProperties = bodyPropertyRepo.getBodyPropertyDtosByResponseIds(ids);

		String rawResponse = ResponseUtils
				.composeRawResponse(ResponseUtils.composeApacheCoreResponse(dto, bodyProperties));

		return Base64.getEncoder().encodeToString(rawResponse.getBytes(StandardCharsets.UTF_8));

	}

	public Response getResponseOfRequest(Long requestId) {

		return responseRepo.getByRequestId(requestId).orElse(null);

	}

	public Page<Response> getResponsesFilteredAndPaged(ResponseFilter filter, Pageable pageable) {

		ResponseUtils.validateResponseFilter(filter)
				.ifNotValidThrow(() -> new IllegalArgumentException("Response filter not valid"));

		Specification<Response> spec = ResponseUtils.buildSpecification(filter);

		return responseRepo.findAll(spec, pageable);

	}

	// NOTE: Logic behind adding a response to existing request and adding the
	// response alongside a new request will be the same

	@Transactional
	public void addResponseToRequest(String responseBase64, Request request) {

		if (responseBase64 == null)
			return;

		String rawResponse = Base64Utils.decode(responseBase64);

		ResponseParseResult parseResult = ResponseUtils.parseResponse(rawResponse);

		parseResult.validationResult()
				.ifNotValidThrow(() -> new IllegalArgumentException(parseResult.validationResult().getMessage()));

		headerService.addHeaders(parseResult.headers(), request.getHost());

		InternalResponsePersistanceDto dto = InternalResponsePersistanceDto.builder()
				.statusCode(parseResult.statusCode()).contentLength(parseResult.contentLength())
				.contentType(parseResult.contentType()).bodyParseResult(parseResult.bodyParseResult()).build();

		Response persistedResponse = this.internalPersistResponse(dto, request);

		request.setResponse(persistedResponse);

	}

	public void deleteResponse(Long responseId) {

	}

	@Transactional
	private Response internalPersistResponse(InternalResponsePersistanceDto dto, Request request) {

		Response response = Response.builder().statusCode(dto.statusCode()).contentLength(dto.contentLength())
				.contentType(dto.contentType()).build();

		response.setRequest(request);

		responseRepo.save(response);

		List<BodyProperty> persistedBodyProperties = bodyService
				.persistResponseBodyProperties(dto.bodyParseResult().getBodyProperties(), response);

		response.setBodyProperties(persistedBodyProperties);

		return response;
	}

}
