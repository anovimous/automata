package com.automata.job.domain.exception;

import com.automata.job.domain.model.enums.JobState;

// Thrown when an operation is not allowed in the current state of the job
public class InvalidJobStateException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidJobStateException(String message) {
		super(message);
	}

	public static InvalidJobStateException requiredState(JobState required, JobState actual, String operation) {
		return new InvalidJobStateException(String.format("The job must be in the %s state in order to %s, but it is %s",
				required, operation, actual));
	}

}
