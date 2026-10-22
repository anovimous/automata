package com.automata.response;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.common.dto.RawHttpMessage;
import com.automata.common.utils.Base64Utils;
import com.automata.common.utils.HttpMessageUtils;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.body.BodyProperty;
import com.automata.request.body.BodyPropertyService;
import com.automata.request.header.HeaderService;
import com.automata.storage.BodyObjectStorage;
import com.automata.storage.StoredBody;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResponseService {

	private final ResponseRepository responseRepo;

	private final RequestRepository requestRepo;

	private final ResponseRawRepository responseRawRepo;

	private final HeaderService headerService;

	private final BodyPropertyService bodyService;

	private final BodyObjectStorage bodyStorage;

	public Response getResponseById(Long responseId) {

		return responseRepo.findById(responseId).orElseThrow(() -> new EntityNotFoundException("Response not found"));

	}

	@Transactional(readOnly = true)
	public RawHttpMessage getRawResponse(Long responseId) {

		ResponseRaw raw = responseRawRepo.findById(responseId)
				.orElseThrow(() -> new EntityNotFoundException("Response not found"));

		return new RawHttpMessage(raw.getRawHeadBase64(), raw.getRawBodyBase64(),
				raw.getResponse().isBodyStoredAsFile());

	}

	@Transactional(readOnly = true)
	public ResponseMetadataDto getResponseMetadata(Long responseId) {

		Response response = this.getResponseById(responseId);

		ResponseRaw raw = responseRawRepo.findById(responseId)
				.orElseThrow(() -> new EntityNotFoundException("Response not found"));

		return ResponseMapper.toMetadataDto(response, raw.getRawHeadBase64());

	}

	// The body of the response wherever it is stored: in object storage or inline in
	// the raw table
	@Transactional(readOnly = true)
	public StoredBody getResponseBody(Long responseId) {

		Response response = this.getResponseById(responseId);

		if (response.isBodyStoredAsFile())
			return bodyStorage.downloadResponseBody(responseId);

		ResponseRaw raw = responseRawRepo.findById(responseId)
				.orElseThrow(() -> new EntityNotFoundException("Response not found"));

		if (raw.getRawBodyBase64() == null)
			throw new EntityNotFoundException("Response has no body");

		String contentType = HttpMessageUtils.contentTypeOfHead(Base64Utils.decodeToBytes(raw.getRawHeadBase64()));

		return StoredBody.inline(Base64Utils.decodeToBytes(raw.getRawBodyBase64()), contentType);

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

		byte[] rawResponse = Base64Utils.decodeToBytes(responseBase64);

		ResponseParseResult parseResult = ResponseUtils.parseResponse(rawResponse);

		parseResult.validationResult()
				.ifNotValidThrow(() -> new IllegalArgumentException(parseResult.validationResult().getMessage()));

		try {
			headerService.addHeaders(parseResult.headers(), request.getHost());
		} catch (Exception e) {
		}

		InternalResponsePersistanceDto dto = InternalResponsePersistanceDto.builder()
				.statusCode(parseResult.statusCode()).contentLength(parseResult.contentLength())
				.contentType(parseResult.contentType()).bodyParseResult(parseResult.bodyParseResult())
				.bodyStoredAsFile(parseResult.bodyStoredAsFile()).bodySizeBytes(parseResult.rawBody().length).build();

		Response persistedResponse = this.internalPersistResponse(dto, request);

		responseRawRepo.save(new ResponseRaw(persistedResponse, Base64Utils.encode(parseResult.rawHead()),
				HttpMessageUtils.encodeInlineBody(parseResult.rawBody(), parseResult.bodyStoredAsFile())));

		if (parseResult.bodyStoredAsFile())
			bodyStorage.storeResponseBody(persistedResponse.getId(), parseResult.rawBody(),
					parseResult.bodyContentType());

		request.setResponse(persistedResponse);

	}

	public void deleteResponse(Long responseId) {

	}

	@Transactional
	private Response internalPersistResponse(InternalResponsePersistanceDto dto, Request request) {

		Response response = Response.builder().statusCode(dto.statusCode()).contentLength(dto.contentLength())
				.contentType(dto.contentType()).bodyStoredAsFile(dto.bodyStoredAsFile())
				.bodySizeBytes(dto.bodySizeBytes()).build();

		response.setRequest(request);

		responseRepo.save(response);

		List<BodyProperty> persistedBodyProperties = bodyService
				.persistResponseBodyProperties(dto.bodyParseResult().getBodyProperties(), response);

		response.setBodyProperties(persistedBodyProperties);

		return response;
	}

}
