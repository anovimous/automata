package com.automata.request;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.automata.common.utils.Base64Utils;
import com.automata.host.common.dto.RequestFilter;
import com.automata.job.JobRepository;
import com.automata.request.body.BodyPropertyService;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.dto.RequestAdditionDto;
import com.automata.request.common.dto.RequestPatchDto;
import com.automata.request.equalityset.RequestEqualitySetRepository;
import com.automata.request.parameter.QueryParameterService;
import com.automata.request.path.PathService;
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

	private final JobRepository jobRepo;

	private final PathService pathService;

	private final QueryParameterService queryParameterService;

	private final BodyPropertyService bodyService;

	public Request getRequestById(Long requestId) {

		return requestRepo.findById(requestId).orElseThrow(() -> new EntityNotFoundException("Request not found"));

	}

	public Page<Request> getRequestsFilteredAndPaged(RequestFilter filter, Pageable pageable) {

		RequestUtils.validateRequestFilter(filter)
				.ifNotValidThrow(() -> new IllegalArgumentException("Request filter not valid"));

		Specification<Request> spec = RequestUtils.buildSpecification(filter);

		return requestRepo.findAll(spec, pageable);

	}

	public Request addRequest(RequestAdditionDto dto) {

		// add request to its own equality set if added with comparator but no equality
		// with something found
		return null;
	}

	public Request addRawRequest(RawRequestAdditionDto dto) {

		// add request to its own equality set if added with comparator but no equality
		// with something found
		return null;
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
			bodyService.updateBodyProvidedRaw(request, dto.contentType().get(),
					Base64Utils.decode(dto.bodyBase64().get()));
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

}
