package com.automata.job.infra;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import org.springframework.stereotype.Service;

import com.automata.job.domain.valueobject.JobDetailsFileContainer;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class S3Service {

	public static final String JOB_CONFIG_BUCKET = "job-configuration";

	private final S3Template s3Template;

	public void storeJobDetailsObject(JobDetailsFileContainer container) {

		s3Template.store(JOB_CONFIG_BUCKET, String.format("details/%d.json", container.getJobId()), container);

	}

	public void storeJobDataFile(String dataTempFileLocation, Long jobId) {

		InputStream is;

		try {
			is = new FileInputStream(dataTempFileLocation);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException("Temporary file could not be accessed/found");
		}

		s3Template.upload(JOB_CONFIG_BUCKET, String.format("data/%d.jsonl", jobId), is);

	}

}
