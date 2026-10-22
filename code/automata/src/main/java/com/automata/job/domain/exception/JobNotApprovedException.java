package com.automata.job.domain.exception;

public class JobNotApprovedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public JobNotApprovedException(Long jobId) {
		super(String.format("Job %d was created by AI and has not been approved yet", jobId));
	}

}
