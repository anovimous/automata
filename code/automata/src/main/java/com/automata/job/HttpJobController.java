package com.automata.job;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.automata.job.common.dto.HttpJobCreationRequest;
import com.automata.job.common.enums.HttpJobScope;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/jobs/http")
@RequiredArgsConstructor
public class HttpJobController {

	private final HttpJobService jobService;

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

}
