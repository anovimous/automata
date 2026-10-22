package com.automata.job.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.enums.ConfigFileStatus;
import com.automata.job.domain.policy.JobPreparationPolicy;
import com.automata.job.domain.valueobject.JobDetailsFileContainer;
import com.automata.job.infra.S3Service;
import com.automata.job.repository.HttpJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobConfigFileAsyncService {

	public static final int MAX_ATTEMPTS = 3;

	private final S3Service s3Service;

	private final JobDetailsService jobDetailsService;

	private final JobDataTmpStorageService tmpStorageService;

	private final HttpJobRepository jobRepo;

	/**
	 * Builds the configuration and data files of a job which is PREPARING, trying
	 * up to {@link #MAX_ATTEMPTS} times. The job ends up READY, or FAILED with the
	 * last error after the files which may have been stored were removed. Storing
	 * the files overwrites them, so running it again is safe.
	 */
	@Async("fileConstructionExecutor")
	public void prepareConfigFiles(Long jobId) {

		HttpJob job = jobRepo.findById(jobId).orElse(null);

		if (job == null) {
			log.warn("Files of job {} can not be prepared since it does not exist anymore", jobId);
			return;
		}

		RuntimeException lastFailure = null;

		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {

			try {

				buildFiles(job);

				markEnded(jobId, ConfigFileStatus.READY, null, attempt);
				return;

			} catch (RuntimeException e) {
				lastFailure = e;
				log.warn("Attempt {} of {} to prepare the files of job {} failed", attempt, MAX_ATTEMPTS, jobId, e);
			}

		}

		removeStoredFiles(jobId);

		markEnded(jobId, ConfigFileStatus.FAILED, describe(lastFailure), MAX_ATTEMPTS);

	}

	private void buildFiles(HttpJob job) {

		JobDetailsFileContainer container = jobDetailsService.createJobDetailsContainer(job);

		s3Service.storeJobDetailsObject(container);

		String dataTempFileLocation = switch (job) {
		case NarrowHttpJob narrow -> tmpStorageService.tempStoreNarrowJobTargetData(narrow);
		case WideHttpJob wide -> tmpStorageService.tempStoreWideJobTargetData(wide);
		default -> throw new IllegalStateException("Files of this kind of job can not be prepared: " + job.getClass());
		};

		try {
			s3Service.storeJobDataFile(dataTempFileLocation, job.getId());
		} finally {
			tmpStorageService.deleteTempFile(dataTempFileLocation);
		}

	}

	// A cleanup which must not hide why the files could not be built
	private void removeStoredFiles(Long jobId) {

		try {
			s3Service.deleteJobDetailsObject(jobId);
		} catch (RuntimeException e) {
			log.warn("Configuration object of job {} could not be removed", jobId, e);
		}

		try {
			s3Service.deleteJobDataFile(jobId);
		} catch (RuntimeException e) {
			log.warn("Data file of job {} could not be removed", jobId, e);
		}

	}

	private void markEnded(Long jobId, ConfigFileStatus status, String failure, int attempts) {

		int updated = jobRepo.updateConfigFileStatus(jobId, ConfigFileStatus.PREPARING, status, failure, attempts);

		if (updated == 0)
			log.warn("Files of job {} were not marked {} since they are not PREPARING anymore", jobId, status);

	}

	private static String describe(RuntimeException e) {
		String message = e.getMessage() != null ? e.getMessage() : "no message";
		return JobPreparationPolicy.limitFailure(e.getClass().getSimpleName() + ": " + message);
	}

}
