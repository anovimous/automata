package com.automata.request;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.common.dto.RawHttpMessage;
import com.automata.common.utils.Base64Utils;
import com.automata.common.utils.HttpMessageUtils;
import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.request.body.BodyProperty;
import com.automata.request.body.BodyPropertyService;
import com.automata.request.common.dto.InternalRequestPersistanceDto;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.dto.RequestFilter;
import com.automata.request.common.dto.RequestMetadataResponse;
import com.automata.request.comparator.ComparatorRepository;
import com.automata.request.equalityset.RequestEqualityRelationRepository;
import com.automata.request.equalityset.RequestEqualitySet;
import com.automata.request.equalityset.RequestEqualitySetRepository;
import com.automata.request.header.HeaderService;
import com.automata.request.parameter.QueryParameter;
import com.automata.request.parameter.QueryParameterService;
import com.automata.request.path.PathService;
import com.automata.request.path.PathVariable;
import com.automata.response.ResponseService;
import com.automata.storage.BodyObjectStorage;
import com.automata.storage.StoredBody;
import com.automata.tenant.Tenant;
import com.automata.tenant.TenantRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RequestService {

	private final RequestRepository requestRepo;

	private final RequestEqualitySetRepository equalitySetRepo;

	private final TenantRepository tenantRepo;

	private final HostRepository hostRepo;

	private final ComparatorRepository comparatorRepo;

	private final RequestRawRepository requestRawRepo;

	private final ResponseService responseService;

	private final BodyObjectStorage bodyStorage;

	private final RequestEqualityRelationRepository equalityRelationRepo;

	private final PathService pathService;

	private final QueryParameterService queryParameterService;

	private final BodyPropertyService bodyService;

	private final HeaderService headerService;

	public Request getRequestById(Long requestId) {

		return requestRepo.findById(requestId).orElseThrow(() -> new EntityNotFoundException("Request not found"));

	}

	@Transactional(readOnly = true)
	public RawHttpMessage getRawRequest(Long requestId) {

		RequestRaw raw = requestRawRepo.findById(requestId)
				.orElseThrow(() -> new EntityNotFoundException("Request not found"));

		return new RawHttpMessage(raw.getRawHeadBase64(), raw.getRawBodyBase64(),
				raw.getRequest().isBodyStoredAsFile());

	}

	@Transactional(readOnly = true)
	public RequestMetadataResponse getRequestMetadata(Long requestId) {

		Request request = this.getRequestById(requestId);

		RequestRaw raw = requestRawRepo.findById(requestId)
				.orElseThrow(() -> new EntityNotFoundException("Request not found"));

		return RequestMapper.toMetadataResponse(request, raw.getRawHeadBase64());

	}

	// The body of the request wherever it is stored: in object storage or inline in
	// the raw table
	@Transactional(readOnly = true)
	public StoredBody getRequestBody(Long requestId) {

		Request request = this.getRequestById(requestId);

		if (request.isBodyStoredAsFile())
			return bodyStorage.downloadRequestBody(requestId);

		RequestRaw raw = requestRawRepo.findById(requestId)
				.orElseThrow(() -> new EntityNotFoundException("Request not found"));

		if (raw.getRawBodyBase64() == null)
			throw new EntityNotFoundException("Request has no body");

		String contentType = HttpMessageUtils.contentTypeOfHead(Base64Utils.decodeToBytes(raw.getRawHeadBase64()));

		return StoredBody.inline(Base64Utils.decodeToBytes(raw.getRawBodyBase64()), contentType);

	}

	public Page<Request> getRequestsFilteredAndPaged(RequestFilter filter, Pageable pageable) {

		RequestUtils.validateRequestFilter(filter)
				.ifNotValidThrow(() -> new IllegalArgumentException("Request filter not valid"));

		Specification<Request> spec = RequestUtils.buildSpecification(filter);

		return requestRepo.findAll(spec, pageable);

	}

	public Page<Request> getEqualRequestsPaged(Long equalitySetId, Pageable pageable) {

		RequestEqualitySet set = equalitySetRepo.findById(equalitySetId)
				.orElseThrow(() -> new EntityNotFoundException("EqualitySet not found"));

		return equalityRelationRepo.findAllInSet(set, pageable);

	}

	// Request and response (with their raw forms) are persisted together
	@Transactional
	public Request addRawRequestWithResponse(RawRequestAdditionDto dto) {

		Request request = this.addRawRequest(dto);

		responseService.addResponseToRequest(dto.responseBase64(), request);

		return request;

	}

	@Transactional
	public Request addRawRequest(RawRequestAdditionDto dto) {

		Host host = hostRepo.findById(dto.hostId()).orElseThrow(() -> new EntityNotFoundException("Host not found"));

		Tenant tenant = null;
		if (dto.tenantId() != null)
			tenant = tenantRepo.findById(dto.tenantId())
					.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		byte[] rawRequest = Base64Utils.decodeToBytes(dto.requestBase64());

		RequestParseResult parseResult = RequestUtils.parseRawRequest(rawRequest);

		parseResult.getValidationResult()
				.ifNotValidThrow(() -> new IllegalArgumentException(parseResult.getValidationResult().getMessage()));

		try {
			headerService.addHeaders(parseResult.getHeaders(), host);
		} catch (Exception e) {
		}

		InternalRequestPersistanceDto internalDto = InternalRequestPersistanceDto.builder().source(dto.source())
				.method(parseResult.getMethod()).version(parseResult.getVersion())
				.extension(parseResult.getPathParseResult().getExtension()).contentType(parseResult.getContentType())
				.requestParseResult(parseResult).program(host.getProgram()).host(host).tenant(tenant).build();

		Request persistedRequest = this.internalPersistRequest(internalDto);

		requestRawRepo.save(new RequestRaw(persistedRequest, Base64Utils.encode(parseResult.getRawHead()),
				HttpMessageUtils.encodeInlineBody(parseResult.getRawBody(), parseResult.isBodyStoredAsFile())));

		if (parseResult.isBodyStoredAsFile())
			bodyStorage.storeRequestBody(persistedRequest.getId(), parseResult.getRawBody(),
					parseResult.getBodyContentType());

		return persistedRequest;

	}

	public void deleteRequest(Long requestId) {
		// check if any unfinished job is tied to it
		// remove equality set if it becomes empty

		throw new RuntimeException("Delete operation is not implemented yet");

//		if (narrowJobTargetRequestRepo.existsByTargetRequestId(requestId))
//			throw new IllegalArgumentException("Deleting a request which is associated with a job is not allowed");

	}

	@Transactional
	private Request internalPersistRequest(InternalRequestPersistanceDto internalDto) {

		Request request = new Request();

		request.setSource(internalDto.getSource());

		request.setMethod(internalDto.getMethod());

		request.setVersion(internalDto.getVersion());

		request.setContentType(internalDto.getContentType());

		request.setComputatedPath(internalDto.getRequestParseResult().getPathParseResult().getComputatedPath());

		request.setExtension(internalDto.getRequestParseResult().getPathParseResult().getExtension());

		request.setNumberOfProperties(
				internalDto.getRequestParseResult().getBodyParseResult().getBodyProperties().size());

		request.setBodyStoredAsFile(internalDto.getRequestParseResult().isBodyStoredAsFile());

		request.setBodySizeBytes(internalDto.getRequestParseResult().getRawBody().length);

		request.setProgram(internalDto.getProgram());

		request.setHost(internalDto.getHost());

		request.setTenant(internalDto.getTenant());

		requestRepo.save(request);

		List<PathVariable> persistedPathVariables = pathService.persistRequestPathVariables(
				internalDto.getRequestParseResult().getPathParseResult().getPathVariables(), request);

		List<QueryParameter> persistedQueryParameters = queryParameterService.persistRequestQueryParameters(
				internalDto.getRequestParseResult().getQueryStringParseResult().getQueryParameters(), request);

		List<BodyProperty> persistedBodyProperties = bodyService.persistRequestBodyProperties(
				internalDto.getRequestParseResult().getBodyParseResult().getBodyProperties(), request);

		request.setPathVariables(persistedPathVariables);

		request.setParameters(persistedQueryParameters);

		request.setBodyProperties(persistedBodyProperties);

		return request;

	}

}
