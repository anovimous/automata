package com.automata.job.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.automata.job.api.dto.JobPollDto;
import com.automata.job.api.dto.JobStateUpdateRequest;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.service.HttpJobService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/external/jobs/http")
@RequiredArgsConstructor
public class HttpJobIntegrationController {

	private final HttpJobService jobService;

	@GetMapping("/poll/{jobId}")
	public ResponseEntity<JobPollDto> pollJob(@PathVariable Long jobId) {

		HttpJob job = jobService.getJob(jobId);

		JobState requestedState = job.getGenericDetails().getRequestedState() == null
				? job.getGenericDetails().getCurrentState()
				: job.getGenericDetails().getRequestedState();

		return ResponseEntity.ok(new JobPollDto(requestedState));

	}

	@PostMapping("/update/{jobId}")
	public ResponseEntity<Void> updateJob(@PathVariable Long jobId, @RequestBody JobStateUpdateRequest req) {

		jobService.updateCurrentJobState(req.jobId(), req.newState(), req.message());

		return ResponseEntity.status(204).build();

	}

}
