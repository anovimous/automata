package com.automata.job.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.WideHttpJob;

import com.automata.job.domain.valueobject.JobDetailsFileContainer;

import com.automata.job.infra.S3Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobConfigFileAsyncService {

	private final S3Service s3Service;

	private final JobDetailsService jobDetailsService;

	private final JobDataTmpStorageService tmpStorageService;

	@Async("fileConstructionExecutor")
	public void createNarrowConfigFile(NarrowHttpJob fullyConfiguredJob) {

		JobDetailsFileContainer container = jobDetailsService.createJobDetailsContainer(fullyConfiguredJob);

		s3Service.storeJobDetailsObject(container);

		String dataTempFileLocation = tmpStorageService.tempStoreNarrowJobTargetData(fullyConfiguredJob);

		s3Service.storeJobDataFile(dataTempFileLocation, fullyConfiguredJob.getId());

	}

	@Async("fileConstructionExecutor")
	public void createWideConfigFile(WideHttpJob fullyConfiguredJob) {

		JobDetailsFileContainer container = jobDetailsService.createJobDetailsContainer(fullyConfiguredJob);

		s3Service.storeJobDetailsObject(container);

		String dataTempFileLocation = tmpStorageService.tempStoreWideJobTargetData(fullyConfiguredJob);

		s3Service.storeJobDataFile(dataTempFileLocation, fullyConfiguredJob.getId());

	}

}
