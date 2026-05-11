package com.automata.job.api;

import java.io.InputStream;
import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.automata.job.api.dto.HttpJobCreationRequest;
import com.automata.job.api.dto.HttpJobResponseDto;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
import com.automata.job.service.HttpJobService;
import com.automata.job.service.HttpJobsSynchronizationService;
import com.automata.program.common.dto.ProgramSummaryDetailsResponseDto;
import com.automata.routine.common.dto.RoutineSummaryDetailsResponseDto;

import io.awspring.cloud.s3.S3Resource;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/jobs/http")
@RequiredArgsConstructor
public class HttpJobController {

	private final HttpJobService jobService;

	private final HttpJobsSynchronizationService syncService;

	@GetMapping("/{jobId}")
	public ResponseEntity<HttpJobResponseDto> getJob(@PathVariable Long jobId) {

		HttpJobFullDetailsInternalDto jobDto = jobService.getJobFullDetails(jobId);

		HttpJobResponseDto responseDto = HttpJobResponseDto.builder().jobId(jobDto.jobId())
				.httpJobScope(jobDto.httpJobScope()).currentState(jobDto.currentState())
				.requestedState(jobDto.requestedState()).verbosity(jobDto.verbosity()).priority(jobDto.priority())
				.rate(jobDto.rate()).targetSelector(jobDto.targetSelector()).genericConfig(jobDto.genericConfig())
				.customConfig(jobDto.customConfig())
				.program(ProgramSummaryDetailsResponseDto.builder().id(jobDto.programId()).name(jobDto.programName())
						.build())
				.routine(RoutineSummaryDetailsResponseDto.builder().id(jobDto.routineId()).key(jobDto.routineKey())
						.build())
				.build();

		return ResponseEntity.ok(responseDto);

	}

	@GetMapping("/{jobId}/result")
	public ResponseEntity<StreamingResponseBody> downloadJobResult(@PathVariable Long jobId) {

		S3Resource resource = jobService.getJobResult(jobId);

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

//	@GetMapping("")
//	public ResponseEntity<PageHolderResponse<HttpJobDto>> getJobs(@RequestBody HttpJobFilter filter) {
//
//	}

	@PostMapping("")
	public ResponseEntity<Void> createNewJob(@RequestBody HttpJobCreationRequest request) {

		HttpJob newlyCreatedJob = switch (request.httpJobScope()) {

		case HttpJobScope.NARROW ->
			jobService.createDraftNarrowJob(request.genericDetails(), request.narrowJobDetails());

		case HttpJobScope.WIDE -> jobService.createDraftWideJob(request.genericDetails(), request.wideJobDetails());

		case HttpJobScope.GLOBAL ->
			jobService.createDraftGlobalJob(request.genericDetails(), request.globalJobDetails());

		default -> throw new IllegalArgumentException("Job type not supported yet");
		};

		return ResponseEntity.created(URI.create(String.format("/api/jobs/http/%d", newlyCreatedJob.getId()))).build();

	}

	@PostMapping("/{draftJobId}/queue")
	public ResponseEntity<Void> queueJob(@PathVariable Long draftJobId) {

		jobService.prepareJobForQueueing(draftJobId);

		return ResponseEntity.status(204).build();
	}

	// TEST METHOD:

	@PostMapping("/sync")
	public ResponseEntity<Void> syncJob() {

		syncService.syncToQueueJobs();

		return ResponseEntity.status(204).build();
	}

	// DELAYED
//	@PostMapping("/{draftJobId}/schedule")
//	public ResponseEntity<Void> scheduleJob(@PathVariable Long draftJobId, @RequestBody JobScheduleRequest req) {
//
//		ScheduleResponse dto = jobService.scheduleJob(draftJobId, req);
//
//		return ResponseEntity.of(dto);
//
//	}

	@PostMapping("/{runningJobId}/pause")
	public ResponseEntity<Void> pauseJob(@PathVariable Long runningJobId) {

		jobService.pauseRunningJob(runningJobId);

		return ResponseEntity.accepted().build();

	}

	@PostMapping("/{pausedJobId}/resume")
	public ResponseEntity<Void> resumeJob(@PathVariable Long pausedJobId) {

		jobService.resumePausedJob(pausedJobId);

		return ResponseEntity.accepted().build();

	}

	@PostMapping("/{jobId}/cancel")
	public ResponseEntity<Void> cancelJob(@PathVariable Long jobId) {

		jobService.cancelJob(jobId);

		return ResponseEntity.status(204).build();

	}

}
