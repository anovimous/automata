package com.automata.job.service;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.JobResult;
import com.automata.job.domain.model.enums.ConfigFileStatus;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.policy.JobPreparationPolicy;
import com.automata.job.domain.policy.JobStateTransitions;
import com.automata.job.domain.policy.JobStateTransitions.ToApplyJobStateChange;
import com.automata.job.repository.HttpJobRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class HttpJobStateService {

	// the states in which a worker explains with a message why the job ended
	private static final Set<JobState> FAILURE_STATES = EnumSet.of(JobState.FAILED, JobState.TERMINATED);

	private final HttpJobRepository jobRepo;

	private final JobConfigFileAsyncService configFileService;

	@Transactional
	public void prepareJobForQueueing(Long draftJobId) {

		HttpJob job = jobRepo.findById(draftJobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		JobPreparationPolicy.requireReadyToPrepareForQueueing(job.getGenericDetails().getConfigFileStatus());

		applyStateChange(job, JobStateTransitions.prepareForQueueing(job.getGenericDetails().getCurrentState(),
				job.getGenericDetails().getRequestedState()));

		jobRepo.save(job);

	}

	/**
	 * Builds the files of a draft job again, when the previous build failed or did
	 * not end. The build starts once the transaction commits.
	 */
	@Transactional
	public void retryConfigFilePreparation(Long draftJobId) {

		HttpJob job = jobRepo.findById(draftJobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		HttpJobGenericDetails details = job.getGenericDetails();

		JobPreparationPolicy.requireRetryable(details.getCurrentState(), details.getConfigFileStatus());

		details.setConfigFileStatus(ConfigFileStatus.PREPARING);
		details.setConfigFileFailure(null);
		details.setConfigFileAttempts(0);

		jobRepo.save(job);

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				configFileService.prepareConfigFiles(draftJobId);
			}
		});

	}

	@Transactional
	public void pauseRunningJob(Long runningJobId) {

		HttpJob job = jobRepo.findById(runningJobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		applyStateChange(job, JobStateTransitions.requestPause(job.getGenericDetails().getCurrentState(),
				job.getGenericDetails().getRequestedState()));

		jobRepo.save(job);

	}

	@Transactional
	public void resumePausedJob(Long pausedJobId) {

		HttpJob job = jobRepo.findById(pausedJobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		applyStateChange(job, JobStateTransitions.requestResume(job.getGenericDetails().getCurrentState(),
				job.getGenericDetails().getRequestedState()));

		jobRepo.save(job);

	}

	@Transactional
	public void cancelJob(Long jobId) {

		HttpJob job = jobRepo.findById(jobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		applyStateChange(job, JobStateTransitions.cancel(job.getGenericDetails().getCurrentState(),
				job.getGenericDetails().getRequestedState()));

		jobRepo.save(job);

	}

	@Transactional
	public void updateCurrentJobState(Long jobId, JobState newState, String message) {

		log.info("Update job state request received: jobId={} newState={}", jobId, newState);
		// free text sent by the worker, so it is kept out of the info level
		log.debug("Update job state message: jobId={} message={}", jobId, message);

		HttpJob job = jobRepo.findById(jobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		JobState previousState = job.getGenericDetails().getCurrentState();

		applyStateChange(job, JobStateTransitions.reportStateAppliedByWorker(previousState,
				job.getGenericDetails().getRequestedState(), newState));

		// a repeated report is not a new failure, so it keeps the message of the first
		if (previousState != newState && FAILURE_STATES.contains(newState) && message != null && !message.isBlank())
			job.setResult(JobResult.failed(message));

		jobRepo.save(job);

	}

	private void applyStateChange(HttpJob job, ToApplyJobStateChange change) {
		job.getGenericDetails().setCurrentState(change.toApplyCurrentState());
		job.getGenericDetails().setRequestedState(change.toApplyRequestedState());
	}

	@Transactional
	public void deleteJob(Long jobId) {

		throw new UnsupportedOperationException("Delete operation is not implemented yet");

	}

}
