package com.automata.job.api;

import java.io.InputStream;
import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.automata.common.dto.response.PageHolderResponse;
import com.automata.job.api.dto.AiJobCreationRequest;
import com.automata.job.api.dto.AiResultRequest;
import com.automata.job.api.dto.HttpJobCreationRequest;
import com.automata.job.api.dto.HttpJobFilter;
import com.automata.job.api.dto.HttpJobResponseDto;
import com.automata.job.api.dto.HttpJobSummaryResponseDto;
import com.automata.job.api.mapper.HttpJobMapper;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
import com.automata.job.domain.valueobject.HttpJobSummaryInternalDto;
import com.automata.job.service.HttpJobCreationService;
import com.automata.job.service.HttpJobQueryService;
import com.automata.job.service.HttpJobStateService;
import com.automata.job.service.HttpJobsSynchronizationService;

import io.awspring.cloud.s3.S3Resource;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/jobs/http")
@RequiredArgsConstructor
public class HttpJobController {

	private final HttpJobCreationService creationService;

	private final HttpJobQueryService queryService;

	private final HttpJobStateService stateService;

	private final HttpJobsSynchronizationService syncService;

	@GetMapping("/{jobId}")
	public ResponseEntity<HttpJobResponseDto> getJob(@PathVariable Long jobId) {

		HttpJobFullDetailsInternalDto jobDto = queryService.getJobFullDetails(jobId);

		HttpJobResponseDto responseDto = HttpJobMapper.toResponseDto(jobDto);

		return ResponseEntity.ok(responseDto);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<HttpJobSummaryResponseDto>> getJobs(@ModelAttribute HttpJobFilter filter,
			@PageableDefault(size = 20, sort = { "creationDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Page<HttpJobSummaryInternalDto> internalDtos = queryService.getAllJobs(filter, pageable);

		Page<HttpJobSummaryResponseDto> responseDtos = HttpJobMapper.toResponseDtos(internalDtos);

		return ResponseEntity.ok(new PageHolderResponse<>(responseDtos));

	}

	@GetMapping("/{jobId}/result")
	public ResponseEntity<StreamingResponseBody> downloadJobResult(@PathVariable Long jobId) {

		S3Resource resource = queryService.getJobResult(jobId);

		StreamingResponseBody stream = outputStream -> {
			try (InputStream inputStream = resource.getInputStream()) {
				inputStream.transferTo(outputStream);
			}
		};

		String contentDispositionHeader = String.format("attachment; filename=\"%d.result\"", jobId);

		return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
				.contentLength(resource.contentLength())
				.header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionHeader).body(stream);

	}

	@PostMapping("")
	public ResponseEntity<Void> createNewJob(@RequestBody HttpJobCreationRequest request) {

		HttpJob newlyCreatedJob = switch (request.httpJobScope()) {

		case HttpJobScope.NARROW ->
			creationService.createDraftNarrowJob(request.genericDetails(), request.narrowJobDetails());

		case HttpJobScope.WIDE -> creationService.createDraftWideJob(request.genericDetails(), request.wideJobDetails());

		};

		return ResponseEntity.created(URI.create(String.format("/api/jobs/http/%d", newlyCreatedJob.getId()))).build();

	}

	@PostMapping("/ai")
	public ResponseEntity<Void> createAiJob(@RequestBody AiJobCreationRequest request) {

		HttpJob newlyCreatedJob = creationService.createDraftAiNarrowJob(request);

		return ResponseEntity.created(URI.create(String.format("/api/jobs/http/%d", newlyCreatedJob.getId()))).build();

	}

	@PostMapping("/{jobId}/approve")
	public ResponseEntity<Void> approveJob(@PathVariable Long jobId) {

		stateService.approveJob(jobId);

		return ResponseEntity.status(204).build();

	}

	@PostMapping("/{jobId}/ai-result")
	public ResponseEntity<Void> setAiResult(@PathVariable Long jobId, @RequestBody AiResultRequest request) {

		stateService.setAiResult(jobId, request.aiResult());

		return ResponseEntity.status(204).build();

	}

	@PostMapping("/{draftJobId}/queue")
	public ResponseEntity<Void> queueJob(@PathVariable Long draftJobId) {

		stateService.prepareJobForQueueing(draftJobId);

		return ResponseEntity.status(204).build();
	}

	@PostMapping("/{draftJobId}/retry-preparation")
	public ResponseEntity<Void> retryPreparation(@PathVariable Long draftJobId) {

		stateService.retryConfigFilePreparation(draftJobId);

		return ResponseEntity.accepted().build();

	}

	// TEST METHOD:

	@PostMapping("/sync")
	public ResponseEntity<Void> syncJob() {

		syncService.syncToQueueJobs();

		return ResponseEntity.status(204).build();
	}

//	@PostMapping("/{draftJobId}/schedule")
//	public ResponseEntity<Void> scheduleJob(@PathVariable Long draftJobId, @RequestBody JobScheduleRequest req) {
//
//		ScheduleResponse dto = stateService.scheduleJob(draftJobId, req);
//
//		return ResponseEntity.of(dto);
//
//	}

	@PostMapping("/{runningJobId}/pause")
	public ResponseEntity<Void> pauseJob(@PathVariable Long runningJobId) {

		stateService.pauseRunningJob(runningJobId);

		return ResponseEntity.accepted().build();

	}

	@PostMapping("/{pausedJobId}/resume")
	public ResponseEntity<Void> resumeJob(@PathVariable Long pausedJobId) {

		stateService.resumePausedJob(pausedJobId);

		return ResponseEntity.accepted().build();

	}

	@PostMapping("/{jobId}/cancel")
	public ResponseEntity<Void> cancelJob(@PathVariable Long jobId) {

		stateService.cancelJob(jobId);

		return ResponseEntity.status(204).build();

	}

	@DeleteMapping("/{jobId}")
	public ResponseEntity<Void> deleteJob(@PathVariable Long jobId) {

		stateService.deleteJob(jobId);

		return ResponseEntity.status(204).build();

	}

}
