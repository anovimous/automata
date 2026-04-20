package com.automata.job.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.automata.common.dto.response.PageHolderResponse;
import com.automata.job.api.dto.HttpJobCreationRequest;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.service.HttpJobService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/jobs/http")
@RequiredArgsConstructor
public class HttpJobController {

	private final HttpJobService jobService;

//	@GetMapping("/{jobId}")
//	public ResponseEntity<HttpJobDto> getJob() {
//
//	}
//
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
