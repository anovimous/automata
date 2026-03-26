package com.automata.job;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.automata.common.dto.response.PageHolderResponse;
import com.automata.job.common.dto.HttpJobCreationRequest;
import com.automata.job.common.enums.HttpJobScope;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/jobs/http")
@RequiredArgsConstructor
public class HttpJobController {

	private final HttpJobService jobService;

	@GetMapping("/{jobId}")
	public ResponseEntity<HttpJobDto> getJob() {

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<HttpJobDto>> getJobs(@RequestBody HttpJobFilter filter) {

	}

	@PostMapping("")
	public ResponseEntity<Void> createDraftJob(@RequestBody HttpJobCreationRequest request) {

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

	}

	@PostMapping("/{draftJobId}/schedule")
	public ResponseEntity<Void> scheduleJob(@PathVariable Long draftJobId, @RequestBody JobScheduleRequest req) {

	}

	@PostMapping("/{jobId}/pause")
	public ResponseEntity<Void> pauseRunningJob(@PathVariable Long draftJobId) {

	}

	@PostMapping("/{jobId}/resume")
	public ResponseEntity<Void> resumePausedJob(@PathVariable Long draftJobId) {

	}

	@PostMapping("/{jobId}/cancel")
	public ResponseEntity<Void> cancelDraftOrPausedJob(@PathVariable Long draftJobId) {

	}

}
