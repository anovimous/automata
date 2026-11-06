package com.automata.job.domain.model;

import com.automata.job.domain.model.enums.FailureCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
	public static final int MAX_FAILURE_MSG_LENGTH = 500;

	@Column(length = MAX_FAILURE_MSG_LENGTH)
	private String failureMsg;

	@Enumerated(EnumType.STRING)
	private FailureCategory failureCategory;

	/**
	 * The result of a job which ended without finishing, with no category known. The message is free text
	 * sent by the worker, so it is cut to what the column holds.
	 */
	public static JobResult failed(String message) {
		return failed(message, FailureCategory.UNKNOWN);
	}

	public static JobResult failed(String message, FailureCategory category) {
		String failureMsg = message == null ? null
				: message.length() > MAX_FAILURE_MSG_LENGTH ? message.substring(0, MAX_FAILURE_MSG_LENGTH) : message;
		return new JobResult(failureMsg, category == null ? FailureCategory.UNKNOWN : category);
	}

}
