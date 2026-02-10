package com.automata.request;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.automata.common.utils.Base64Utils;
import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.host.common.dto.RequestFilter;
import com.automata.job.RunJobRepository;
import com.automata.request.body.BodyProperty;
import com.automata.request.body.BodyPropertyService;
import com.automata.request.common.dto.InternalRequestPersistanceDto;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.dto.RequestAdditionDto;
import com.automata.request.common.dto.RequestPatchDto;
import com.automata.request.comparator.ComparatorRepository;
import com.automata.request.equalityset.RequestEqualitySetRepository;
import com.automata.request.header.HeaderService;
import com.automata.request.parameter.QueryParameter;
import com.automata.request.parameter.QueryParameterService;
import com.automata.request.path.PathService;
import com.automata.request.path.PathVariable;
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

	private final RunJobRepository jobRepo;

	private final HostRepository hostRepo;

	private final ComparatorRepository comparatorRepo;

	private final PathService pathService;

	private final QueryParameterService queryParameterService;

	private final BodyPropertyService bodyService;

	private final HeaderService headerService;

	public Request getRequestById(Long requestId) {

		return requestRepo.findById(requestId).orElseThrow(() -> new EntityNotFoundException("Request not found"));

	}

	public Page<Request> getRequestsFilteredAndPaged(RequestFilter filter, Pageable pageable) {

		RequestUtils.validateRequestFilter(filter)
				.ifNotValidThrow(() -> new IllegalArgumentException("Request filter not valid"));

		Specification<Request> spec = RequestUtils.buildSpecification(filter);

		return requestRepo.findAll(spec, pageable);

	}

	public Request addRequestViaComponents(RequestAdditionDto dto) {
		// DELAYED
		return null;

	}

	public Request addRawRequest(RawRequestAdditionDto dto) {

		Host host = hostRepo.findById(dto.hostId()).orElseThrow(() -> new EntityNotFoundException("Host not found"));

		Tenant tenant = tenantRepo.findById(dto.tenantId()).orElse(null);

		String rawRequest = Base64Utils.decode(dto.requestBase64());

		RequestParseResult parseResult = RequestUtils.parseRawRequest(rawRequest);

		parseResult.getValidationResult()
				.ifNotValidThrow(() -> new IllegalArgumentException(parseResult.getValidationResult().getMessage()));

		headerService.addHeaders(parseResult.getHeaders(), host);

		InternalRequestPersistanceDto internalDto = InternalRequestPersistanceDto.builder().source(dto.source())
				.method(parseResult.getMethod()).version(parseResult.getVersion())
				.extension(parseResult.getPathParseResult().getExtension()).contentType(parseResult.getContentType())
				.requestParseResult(parseResult).build();

		Request persistedRequest = this.internalPersistRequest(internalDto);

		persistedRequest.setHost(host);

		persistedRequest.setProgram(host.getProgram());

		persistedRequest.setTenant(tenant);

		return persistedRequest;

	}

	public Request patchRequest(Long requestId, RequestPatchDto dto) {

		Request request = requestRepo.findById(requestId)
				.orElseThrow(() -> new EntityNotFoundException("Request not found"));

		if (jobRepo.existsByRequest(request))
			throw new RuntimeException("Request can't be modified if it is related to any job");

		if (equalitySetRepo.existsByRequests_Id(requestId))
			throw new RuntimeException("Request can't be modified if it exists in an equalityset");

		dto.method().ifPresent(request::setMethod);

		dto.source().ifPresent(request::setSource);

		dto.tenantId().ifPresent((tenantId) -> {

			Tenant tenant = tenantRepo.findById(tenantId)
					.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

			request.setTenant(tenant);

		});

		dto.path().ifPresentOrElse(path -> pathService.updatePath(request, path), () -> {
			dto.extension().ifPresent(request::setExtension);
		});

		dto.queryStringBase64().ifPresent(queryStringBase64 -> queryParameterService
				.updateQueryParametersProvidedQueryString(request, Base64Utils.decode(queryStringBase64)));

		if (dto.bodyBase64().isPresent() && dto.contentType().isPresent())
			bodyService.updateBodyProvidedRaw(request, Base64Utils.decode(dto.bodyBase64().get()));
		else {
			if (dto.bodyBase64().isPresent() || dto.contentType().isPresent())
				throw new IllegalArgumentException(
						"Body and contentType must be provided in conjunction with each other");
		}

		return request;

	}

	public void deleteRequest(Long requestId) {
		// check if any unfinished job is tied to it
		// remove equality set if it becomes empty

	}

	private Request internalPersistRequest(InternalRequestPersistanceDto internalDto) {

		Request request = new Request();

		request.setSource(internalDto.getSource());

		request.setMethod(internalDto.getMethod());

		request.setVersion(internalDto.getVersion());

		request.setContentType(internalDto.getContentType());

		request.setComputatedPath(internalDto.getRequestParseResult().getPathParseResult().getComputatedPath());

		request.setExtension(internalDto.getRequestParseResult().getPathParseResult().getExtension());

		requestRepo.save(request);

		List<PathVariable> persistedPathVariables = pathService
				.persistPathVariables(internalDto.getRequestParseResult().getPathParseResult().getPathVariables());

		List<QueryParameter> persistedQueryParameters = queryParameterService.persistQueryParameters(
				internalDto.getRequestParseResult().getQueryStringParseResult().getQueryParameters());

		List<BodyProperty> persistedBodyProperties = bodyService.persistRequestBodyProperties(
				internalDto.getRequestParseResult().getBodyParseResult().getBodyProperties(), request);

		request.setPathVariables(persistedPathVariables);

		request.setParameters(persistedQueryParameters);

		request.setBodyProperties(persistedBodyProperties);

		return request;

	}

}
