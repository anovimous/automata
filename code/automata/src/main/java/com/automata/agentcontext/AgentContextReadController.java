package com.automata.agentcontext;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.automata.agentcontext.common.dto.AgentContextTenantResponse;
import com.automata.agentcontext.common.dto.ResolvedTarget;
import com.automata.common.dto.RawHttpMessage;
import com.automata.host.HostMapper;
import com.automata.host.common.dto.HostDetailedResponse;
import com.automata.job.api.dto.RequestJobResponseDto;
import com.automata.job.api.dto.RequestJobSummaryResponseDto;
import com.automata.job.api.mapper.HttpJobMapper;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.JobResultFile;
import com.automata.job.domain.valueobject.RequestJobSummaryInternalDto;
import com.automata.job.service.HttpJobQueryService;
import com.automata.common.dto.response.PageHolderResponse;
import com.automata.request.RequestMapper;
import com.automata.request.RequestService;
import com.automata.request.common.dto.RequestMetadataResponse;
import com.automata.request.common.dto.RequestRawResponse;
import com.automata.response.RawResponseDto;
import com.automata.response.Response;
import com.automata.response.ResponseMetadataDto;
import com.automata.response.ResponseService;
import com.automata.storage.BodyDownloads;
import com.automata.storage.StoredBody;

import io.awspring.cloud.s3.S3Resource;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

// Reads of the requests and responses a context gives access to. A request that is
// not in the context answers 404, like a request that does not exist. The response
// is addressed through its request.
@RestController
@RequestMapping("/api/agent-contexts/uuid/{uuid}")
@RequiredArgsConstructor
public class AgentContextReadController {

	private final AgentContextService contextService;

	private final RequestService requestService;

	private final ResponseService responseService;

	private final HttpJobQueryService jobQueryService;

	@GetMapping("/resolved-requests")
	public ResponseEntity<List<ResolvedTarget>> getResolvedRequests(@PathVariable UUID uuid) {

		Long contextId = contextService.getContextByUuid(uuid).getId();

		return ResponseEntity.ok(contextService.resolveContextTargets(contextId));

	}

	@GetMapping("/host")
	public ResponseEntity<HostDetailedResponse> getHost(@PathVariable UUID uuid) {

		return ResponseEntity.ok(HostMapper.toDetailedResponse(contextService.getContextHost(uuid)));

	}

	@GetMapping("/tenants")
	public ResponseEntity<List<AgentContextTenantResponse>> getTenants(@PathVariable UUID uuid) {

		return ResponseEntity.ok(contextService.getContextTenants(uuid));

	}

	@GetMapping("/requests/{requestId}/metadata")
	public ResponseEntity<RequestMetadataResponse> getRequestMetadata(@PathVariable UUID uuid,
			@PathVariable Long requestId) {

		contextService.requireRequestInContext(uuid, requestId);

		return ResponseEntity.ok(requestService.getRequestMetadata(requestId));

	}

	@GetMapping("/requests/{requestId}/raw")
	public ResponseEntity<RequestRawResponse> getRawRequest(@PathVariable UUID uuid, @PathVariable Long requestId) {

		contextService.requireRequestInContext(uuid, requestId);

		RawHttpMessage rawRequest = requestService.getRawRequest(requestId);

		Response response = responseService.getResponseOfRequest(requestId);

		return ResponseEntity.ok(RequestMapper.toRawResponse(rawRequest, response != null ? response.getId() : null));

	}

	@GetMapping("/requests/{requestId}/body/stream")
	public ResponseEntity<InputStreamResource> streamRequestBody(@PathVariable UUID uuid,
			@PathVariable Long requestId) {

		contextService.requireRequestInContext(uuid, requestId);

		StoredBody body = requestService.getRequestBody(requestId);

		return BodyDownloads.attachment(body, "request-" + requestId);

	}

	// The jobs that ever targeted the request, also those of other contexts and of users. They
	// are read only and never say which other context they belong to.
	@GetMapping("/requests/{requestId}/jobs")
	public ResponseEntity<PageHolderResponse<RequestJobSummaryResponseDto>> getRequestJobs(@PathVariable UUID uuid,
			@PathVariable Long requestId, @RequestParam(required = false) Set<JobState> states,
			@PageableDefault(size = 20, sort = { "creationDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		contextService.requireRequestInContext(uuid, requestId);

		Page<RequestJobSummaryInternalDto> jobs = jobQueryService.getJobsOfRequest(uuid, requestId, states,
				pageable);

		return ResponseEntity.ok(new PageHolderResponse<>(jobs.map(HttpJobMapper::toResponseDto)));

	}

	@GetMapping("/requests/{requestId}/jobs/{jobId}")
	public ResponseEntity<RequestJobResponseDto> getRequestJob(@PathVariable UUID uuid, @PathVariable Long requestId,
			@PathVariable Long jobId) {

		contextService.requireRequestInContext(uuid, requestId);

		jobQueryService.getJobOfRequest(requestId, jobId);

		return ResponseEntity.ok(
				HttpJobMapper.toRequestJobResponseDto(jobQueryService.getJobFullDetails(jobId), uuid));

	}

	@GetMapping("/requests/{requestId}/jobs/{jobId}/result")
	public ResponseEntity<StreamingResponseBody> getRequestJobResult(@PathVariable UUID uuid,
			@PathVariable Long requestId, @PathVariable Long jobId) {

		contextService.requireRequestInContext(uuid, requestId);

		jobQueryService.getJobOfRequest(requestId, jobId);

		JobResultFile result = jobQueryService.getJobResult(jobId);

		S3Resource resource = result.resource();

		StreamingResponseBody stream = outputStream -> {
			try (InputStream inputStream = resource.getInputStream()) {
				inputStream.transferTo(outputStream);
			}
		};

		return ResponseEntity.ok().contentType(result.contentType().mediaType()).contentLength(resource.contentLength())
				.body(stream);

	}

	@GetMapping("/requests/{requestId}/response/metadata")
	public ResponseEntity<ResponseMetadataDto> getResponseMetadata(@PathVariable UUID uuid,
			@PathVariable Long requestId) {

		Long responseId = this.responseIdOfRequestInContext(uuid, requestId);

		return ResponseEntity.ok(responseService.getResponseMetadata(responseId));

	}

	@GetMapping("/requests/{requestId}/response/raw")
	public ResponseEntity<RawResponseDto> getRawResponse(@PathVariable UUID uuid, @PathVariable Long requestId) {

		Long responseId = this.responseIdOfRequestInContext(uuid, requestId);

		RawHttpMessage rawResponse = responseService.getRawResponse(responseId);

		return ResponseEntity.ok(new RawResponseDto(rawResponse.rawHeadBase64(), rawResponse.rawBodyBase64(),
				rawResponse.bodyStoredAsFile()));

	}

	@GetMapping("/requests/{requestId}/response/body/stream")
	public ResponseEntity<InputStreamResource> streamResponseBody(@PathVariable UUID uuid,
			@PathVariable Long requestId) {

		Long responseId = this.responseIdOfRequestInContext(uuid, requestId);

		StoredBody body = responseService.getResponseBody(responseId);

		return BodyDownloads.attachment(body, "response-" + responseId);

	}

	private Long responseIdOfRequestInContext(UUID uuid, Long requestId) {

		contextService.requireRequestInContext(uuid, requestId);

		Response response = responseService.getResponseOfRequest(requestId);

		if (response == null)
			throw new EntityNotFoundException("Response not found");

		return response.getId();

	}

}
