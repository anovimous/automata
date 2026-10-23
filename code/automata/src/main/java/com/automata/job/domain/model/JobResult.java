package com.automata.job.domain.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobResult {

	// the length of the failure_msg column (see the Flyway migrations)
	public static final int MAX_FAILURE_MSG_LENGTH = 255;

	private String failureMsg;

	/**
	 * The result of a job which ended without finishing. The message is free text
	 * sent by the worker, so it is cut to what the column holds.
	 */
	public static JobResult failed(String message) {
		String failureMsg = message.length() > MAX_FAILURE_MSG_LENGTH ? message.substring(0, MAX_FAILURE_MSG_LENGTH)
				: message;
		return new JobResult(failureMsg);
	}

}
