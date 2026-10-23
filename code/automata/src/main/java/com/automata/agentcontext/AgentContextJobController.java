package com.automata.agentcontext;

import java.io.InputStream;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.automata.common.dto.response.PageHolderResponse;
import com.automata.job.api.dto.AiJobCreationRequest;
import com.automata.job.api.dto.AiResultRequest;
import com.automata.job.api.dto.HttpJobFilter;
import com.automata.job.api.dto.HttpJobResponseDto;
import com.automata.job.api.dto.HttpJobSummaryResponseDto;
import com.automata.job.api.mapper.HttpJobMapper;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobSummaryInternalDto;
import com.automata.job.domain.valueobject.JobResultFile;
import com.automata.job.service.HttpJobCreationService;
import com.automata.job.service.HttpJobQueryService;
import com.automata.job.service.HttpJobStateService;

import io.awspring.cloud.s3.S3Resource;
import lombok.RequiredArgsConstructor;

// The job operations of the AI agent. They only reach the AI jobs of the context in
// the path: any other job answers 404, like a job that does not exist.
@RestController
@RequestMapping("/api/agent-contexts/uuid/{uuid}/jobs")
@RequiredArgsConstructor
public class AgentContextJobController {

	private final AgentContextService contextService;

	private final HttpJobCreationService creationService;

	private final HttpJobQueryService queryService;

	private final HttpJobStateService stateService;

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<HttpJobSummaryResponseDto>> getJobs(@PathVariable UUID uuid,
			@RequestParam(required = false) Set<JobState> states,
			@PageableDefault(size = 20, sort = { "creationDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Long contextId = contextService.getContextByUuid(uuid).getId();

		HttpJobFilter filter = new HttpJobFilter(null, null, null, null, states, JobCreator.AI, contextId, null);

		Page<HttpJobSummaryInternalDto> internalDtos = queryService.getAllJobs(filter, pageable);

		return ResponseEntity.ok(new PageHolderResponse<>(HttpJobMapper.toResponseDtos(internalDtos)));

	}

	@GetMapping("/{jobId}")
	public ResponseEntity<HttpJobResponseDto> getJob(@PathVariable UUID uuid, @PathVariable Long jobId) {

		queryService.getJobOfContext(uuid, jobId);

		return ResponseEntity.ok(HttpJobMapper.toResponseDto(queryService.getJobFullDetails(jobId)));

	}

	@GetMapping("/{jobId}/result")
	public ResponseEntity<StreamingResponseBody> getJobResult(@PathVariable UUID uuid, @PathVariable Long jobId) {

		queryService.getJobOfContext(uuid, jobId);

		JobResultFile result = queryService.getJobResult(jobId);

		S3Resource resource = result.resource();

		StreamingResponseBody stream = outputStream -> {
			try (InputStream inputStream = resource.getInputStream()) {
				inputStream.transferTo(outputStream);
			}
		};

		return ResponseEntity.ok().contentType(result.contentType().mediaType()).contentLength(resource.contentLength())
				.body(stream);

	}

	@PostMapping("")
	public ResponseEntity<Void> createJob(@PathVariable UUID uuid, @RequestBody AiJobCreationRequest request) {

		HttpJob newlyCreatedJob = creationService.createDraftAiNarrowJob(uuid, request);

		return ResponseEntity
				.created(URI.create(String.format("/api/agent-contexts/uuid/%s/jobs/%d", uuid, newlyCreatedJob.getId())))
				.build();

	}

	@PostMapping("/{jobId}/queue")
	public ResponseEntity<Void> queueJob(@PathVariable UUID uuid, @PathVariable Long jobId) {

		queryService.getJobOfContext(uuid, jobId);

		stateService.prepareJobForQueueing(jobId);

		return ResponseEntity.status(204).build();

	}

	@PostMapping("/{jobId}/retry-preparation")
	public ResponseEntity<Void> retryPreparation(@PathVariable UUID uuid, @PathVariable Long jobId) {

		queryService.getJobOfContext(uuid, jobId);

		stateService.retryConfigFilePreparation(jobId);

		return ResponseEntity.accepted().build();

	}

	@PostMapping("/{jobId}/ai-result")
	public ResponseEntity<Void> setAiResult(@PathVariable UUID uuid, @PathVariable Long jobId,
			@RequestBody AiResultRequest request) {

		queryService.getJobOfContext(uuid, jobId);

		stateService.setAiResult(jobId, request.aiResult());

		return ResponseEntity.status(204).build();

	}

	@PostMapping("/{jobId}/cancel")
	public ResponseEntity<Void> cancelJob(@PathVariable UUID uuid, @PathVariable Long jobId) {

		queryService.getJobOfContext(uuid, jobId);

		stateService.cancelJob(jobId);

		return ResponseEntity.status(204).build();

	}

}
