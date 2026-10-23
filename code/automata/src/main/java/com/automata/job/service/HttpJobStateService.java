package com.automata.job.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.exception.JobNotApprovedException;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.JobResult;
import com.automata.job.domain.model.enums.ConfigFileStatus;
import com.automata.job.domain.model.enums.JobCreator;
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
	private static final Set<JobState> FAILURE_STATES = EnumSet.of(JobState.FAILED, JobState.SELFTERMINATED);

	private static final Set<JobState> TERMINAL_STATES = EnumSet.of(JobState.FINISHED, JobState.FAILED,
			JobState.SELFTERMINATED, JobState.CANCELED, JobState.LOSTCONTACT);

	// how long a job which a worker holds may go without any contact from it
	public static final Duration LOST_CONTACT_AFTER = Duration.ofMinutes(30);

	private final HttpJobRepository jobRepo;

	private final JobConfigFileAsyncService configFileService;

	@Transactional
	public void prepareJobForQueueing(Long draftJobId) {

		HttpJob job = jobRepo.findById(draftJobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		if (job.getCreatedBy() == JobCreator.AI && !job.isApproved())
			throw new JobNotApprovedException(draftJobId);

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

		job.getGenericDetails().setLastWorkerContactAt(Instant.now());

		jobRepo.save(job);

	}

	/**
	 * A worker has asked about the job, which shows that it is alive.
	 */
	@Transactional
	public void recordWorkerContact(Long jobId) {

		if (jobRepo.updateLastWorkerContact(jobId, Instant.now()) == 0)
			throw new EntityNotFoundException("HttpJob with the given id has not been found");

	}

	/**
	 * Moves the running and paused jobs of which no worker has been in contact for
	 * the given time to LOSTCONTACT, which frees their share of the rate limits.
	 *
	 * @return the number of jobs moved
	 */
	@Transactional
	public int markJobsWithLostContact(Instant now, Duration after) {

		List<HttpJob> lostJobs = jobRepo.findAllByCurrentStateInAndLastWorkerContactBefore(
				List.of(JobState.RUNNING, JobState.PAUSED), now.minus(after));

		for (HttpJob job : lostJobs) {

			applyStateChange(job, JobStateTransitions.loseContact(job.getGenericDetails().getCurrentState(),
					job.getGenericDetails().getRequestedState()));

			job.setResult(JobResult.failed("No worker has been in contact about the job for " + after.toMinutes()
					+ " minutes, so it is considered lost"));

			jobRepo.save(job);

			log.warn("Job lost contact with its worker: jobId={}", job.getId());

		}

		return lostJobs.size();

	}

	private void applyStateChange(HttpJob job, ToApplyJobStateChange change) {
		job.getGenericDetails().setCurrentState(change.toApplyCurrentState());
		job.getGenericDetails().setRequestedState(change.toApplyRequestedState());
	}

	@Transactional
	public void approveJob(Long jobId) {

		HttpJob job = jobRepo.findById(jobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		if (job.getCreatedBy() != JobCreator.AI)
			throw new InvalidJobStateException("Only AI-created jobs can be approved");

		if (job.getGenericDetails().getCurrentState() != JobState.DRAFT)
			throw InvalidJobStateException.requiredState(JobState.DRAFT,
					job.getGenericDetails().getCurrentState(), "approve");

		job.setApproved(true);
		jobRepo.save(job);

	}

	@Transactional
	public void setAiResult(Long jobId, String aiResult) {

		if (aiResult != null && aiResult.length() > HttpJob.MAX_AI_RESULT_LENGTH)
			throw new IllegalArgumentException(
					"aiResult is too long, the limit is " + HttpJob.MAX_AI_RESULT_LENGTH + " characters");

		HttpJob job = jobRepo.findById(jobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		if (job.getCreatedBy() != JobCreator.AI)
			throw new InvalidJobStateException("Only AI-created jobs can have an AI result set");

		JobState state = job.getGenericDetails().getCurrentState();
		if (!TERMINAL_STATES.contains(state))
			throw new InvalidJobStateException(
					String.format("Job must be in a terminal state to set AI result, but it is %s", state));

		job.setAiResult(aiResult);
		jobRepo.save(job);

	}

	@Transactional
	public void deleteJob(Long jobId) {

		throw new UnsupportedOperationException("Delete operation is not implemented yet");

	}

}
