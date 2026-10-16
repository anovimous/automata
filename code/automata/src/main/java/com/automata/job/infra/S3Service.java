package com.automata.job.infra;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Service;

import com.automata.job.domain.valueobject.JobDetailsFileContainer;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class S3Service {

	public static final String JOB_CONFIG_BUCKET = "job-configuration";

	public static final String JOB_RESULT_BUCKET = "job-results";

	private final S3Template s3Template;

	public S3Resource getJobResult(Long jobId) {

		String resultKey = String.format("%d.result", jobId);

		return s3Template.download(JOB_RESULT_BUCKET, resultKey);

	}

	public void storeJobDetailsObject(JobDetailsFileContainer container) {

		s3Template.store(JOB_CONFIG_BUCKET, String.format("details/%d.json", container.jobId()), container);

	}

	public void storeJobDataFile(String dataTempFileLocation, Long jobId) {

		try (InputStream is = new FileInputStream(dataTempFileLocation)) {

			s3Template.upload(JOB_CONFIG_BUCKET, String.format("data/%d.jsonl", jobId), is);

		} catch (FileNotFoundException e) {
			throw new RuntimeException("Temporary file could not be accessed/found", e);
		} catch (IOException e) {
			throw new RuntimeException("Temporary file could not be read", e);
		}

	}

}
