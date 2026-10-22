package com.automata.job.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.job.api.dto.HttpJobFilter;
import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
import com.automata.job.domain.valueobject.JobResultFile;
import com.automata.job.domain.valueobject.HttpJobSummaryInternalDto;
import com.automata.job.infra.S3Service;
import com.automata.job.repository.HttpJobRepository;
import com.automata.job.utils.HttpJobUtils;

import io.awspring.cloud.s3.S3Resource;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HttpJobQueryService {

	private final HttpJobRepository jobRepo;

	private final S3Service s3;

	@Transactional(readOnly = true)
	public HttpJob getJob(Long jobId) {

		return jobRepo.findById(jobId).orElseThrow(() -> new EntityNotFoundException("Job not found"));

	}

	// The job when it is an AI job of the given context. Any other job, the jobs of
	// other contexts and the jobs of users included, answers as if it did not exist.
	@Transactional(readOnly = true)
	public HttpJob getJobOfContext(UUID contextUuid, Long jobId) {

		HttpJob job = this.getJob(jobId);

		if (job.getCreatedBy() != JobCreator.AI || job.getAgentContext() == null
				|| !job.getAgentContext().getUuid().equals(contextUuid))
			throw new EntityNotFoundException("Job not found");

		return job;

	}

	@Transactional(readOnly = true)
	public Page<HttpJobSummaryInternalDto> getAllJobs(HttpJobFilter filter, Pageable pageable) {

		Specification<HttpJob> spec = HttpJobUtils.buildSpecification(filter);

		return jobRepo.findAll(spec, pageable).map(HttpJobSummaryInternalDto::of);

	}

	@Transactional(readOnly = true)
	public JobResultFile getJobResult(Long jobId) {

		HttpJob job = jobRepo.findById(jobId).orElseThrow(() -> new EntityNotFoundException("Job not found"));

		if (job.getGenericDetails().getCurrentState() != JobState.FINISHED
				&& job.getGenericDetails().getCurrentState() != JobState.FAILED)
			throw new InvalidJobStateException("The job has to end before downloading its result");

		S3Resource result = s3.getJobResult(job.getId());

		if (!result.exists())
			throw new EntityNotFoundException("Result not found");

		return new JobResultFile(result, job.getRoutine().getResultContentType());

	}

	@Transactional(readOnly = true)
	public HttpJobFullDetailsInternalDto getJobFullDetails(Long jobId) {

		HttpJobFullDetailsInternalDto jobDto = jobRepo.findFullDetailsByJobId(jobId)
				.orElseThrow(() -> new EntityNotFoundException("Job not found"));

		return jobDto;

	}

}
