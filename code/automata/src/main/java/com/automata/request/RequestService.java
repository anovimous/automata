package com.automata.request;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.common.utils.Base64Utils;
import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.job.domain.valueobject.RequestInternalDto;
import com.automata.request.body.BodyProperty;
import com.automata.request.body.BodyPropertyRepository;
import com.automata.request.body.BodyPropertyService;
import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.dto.InternalRequestPersistanceDto;
import com.automata.request.common.dto.PathVariableInternalDto;
import com.automata.request.common.dto.QueryParameterInternalDto;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.dto.RequestFilter;
import com.automata.request.common.dto.RequestPatchDto;
import com.automata.request.comparator.ComparatorRepository;
import com.automata.request.equalityset.RequestEqualitySetRepository;
import com.automata.request.header.HeaderService;
import com.automata.request.parameter.QueryParameter;
import com.automata.request.parameter.QueryParameterRepository;
import com.automata.request.parameter.QueryParameterService;
import com.automata.request.path.PathService;
import com.automata.request.path.PathVariable;
import com.automata.request.path.PathVariableRepository;
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

	private final PathVariableRepository pathVariableRepo;

	private final QueryParameterRepository queryParameterRepo;

	private final BodyPropertyRepository bodyPropertyRepo;

	private final PathService pathService;

	private final QueryParameterService queryParameterService;

	private final BodyPropertyService bodyService;

	private final HeaderService headerService;

	public Request getRequestById(Long requestId) {

		return requestRepo.findById(requestId).orElseThrow(() -> new EntityNotFoundException("Request not found"));

	}

	@Transactional(readOnly = true)
	public String getRawRequestBase64(Long requestId) {

		Request request = requestRepo.findById(requestId)
				.orElseThrow(() -> new EntityNotFoundException("Request not found"));

		RequestInternalDto dto = RequestInternalDto.builder().requestId(requestId).method(request.getMethod())
				.computatedPath(request.getComputatedPath()).version(request.getVersion())
				.extension(request.getExtension()).numberOfProperties(request.getNumberOfProperties())
				.contentType(request.getContentType()).source(request.getSource()).build();

		List<Long> ids = List.of(request.getId());

		List<PathVariableInternalDto> pathVariables = pathVariableRepo.getPathVariableDtosOfRequests(ids);

		List<QueryParameterInternalDto> queryParameters = queryParameterRepo.getQueryParameterDtosByRequestsIds(ids);

		List<BodyPropertyInternalDto> bodyProperties = bodyPropertyRepo.getBodyPropertyDtosByRequestsIds(ids);

		dto.setPathVariableDtos(pathVariables);

		dto.setQueryParameterDtos(queryParameters);

		dto.setBodyPropertyDtos(bodyProperties);

		String host = request.getHost().getHost();

		String rawRequest = RequestUtils.composeRawRequest(RequestUtils.composeApacheCoreRequest(dto, host),
				request.getVersion());

		return Base64.getEncoder().encodeToString(rawRequest.getBytes(StandardCharsets.UTF_8));

	}

	public Page<Request> getRequestsFilteredAndPaged(RequestFilter filter, Pageable pageable) {

		RequestUtils.validateRequestFilter(filter)
				.ifNotValidThrow(() -> new IllegalArgumentException("Request filter not valid"));

		Specification<Request> spec = RequestUtils.buildSpecification(filter);

		return requestRepo.findAll(spec, pageable);

	}

	@Transactional
	public Request addRawRequest(RawRequestAdditionDto dto) {

		Host host = hostRepo.findById(dto.hostId()).orElseThrow(() -> new EntityNotFoundException("Host not found"));

		Tenant tenant = null;
		if (dto.tenantId() != null)
			tenant = tenantRepo.findById(dto.tenantId())
					.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		String rawRequest = Base64Utils.decode(dto.requestBase64());

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

		return persistedRequest;

	}

	@Transactional
	public Request patchRequest(Long requestId, RequestPatchDto dto) {

		throw new RuntimeException("Patch operation is not implemented yet");

//		Request request = requestRepo.findById(requestId)
//				.orElseThrow(() -> new EntityNotFoundException("Request not found"));
//
//		if (narrowJobTargetRequestRepo.existsByTargetRequestId(requestId))
//			throw new IllegalArgumentException("Updating a request which is associated with a job is not allowed");
//
//		if (equalitySetRepo.existsByRequests_Id(requestId))
//			throw new RuntimeException("Request can't be modified if it exists in an equalityset");
//
//		dto.method().ifPresent(request::setMethod);
//
//		dto.source().ifPresent(request::setSource);
//
//		dto.tenantId().ifPresent((tenantId) -> {
//
//			Tenant tenant = tenantRepo.findById(tenantId)
//					.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
//
//			request.setTenant(tenant);
//
//		});
//
//		dto.path().ifPresentOrElse(path -> pathService.updatePath(request, path), () -> {
//			dto.extension().ifPresent(request::setExtension);
//		});
//
//		dto.queryStringBase64().ifPresent(queryStringBase64 -> queryParameterService
//				.updateQueryParametersProvidedQueryString(request, Base64Utils.decode(queryStringBase64)));
//
//		if (dto.bodyBase64().isPresent() && dto.contentType().isPresent())
//			bodyService.updateBodyProvidedRaw(request, Base64Utils.decode(dto.bodyBase64().get()));
//		else {
//			if (dto.bodyBase64().isPresent() || dto.contentType().isPresent())
//				throw new IllegalArgumentException(
//						"Body and contentType must be provided in conjunction with each other");
//		}
//
//		return request;

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
