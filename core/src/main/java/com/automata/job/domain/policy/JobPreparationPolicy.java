package com.automata.job.domain.policy;

import java.util.Set;

import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.model.enums.ConfigFileStatus;
import com.automata.job.domain.model.enums.JobState;

/**
 * The rules about the preparation of the files of a job, which is independent
 * of the state of the job. Each method throws {@link InvalidJobStateException}
 * when the rule is broken, and never modifies anything.
 */
public final class JobPreparationPolicy {

	public static final int MAX_FAILURE_LENGTH = 255;

	// A job which is still preparing is retryable too, since a build which died
	// with the application would never end otherwise
	private static final Set<ConfigFileStatus> RETRYABLE = Set.of(ConfigFileStatus.PREPARING,
			ConfigFileStatus.FAILED);

	private JobPreparationPolicy() {
	}

	/**
	 * A job can only be queued once its files exist.
	 */
	public static void requireReadyToPrepareForQueueing(ConfigFileStatus status) {

		if (status != ConfigFileStatus.READY)
			throw new InvalidJobStateException(
					"The files of the job must be READY in order to prepare it for queueing, but they are " + status);

	}

	/**
	 * The files can only be built again for a draft job whose build did not end well.
	 */
	public static void requireRetryable(JobState current, ConfigFileStatus status) {

		if (current != JobState.DRAFT)
			throw InvalidJobStateException.requiredState(JobState.DRAFT, current, "retry the preparation of its files");

		if (!RETRYABLE.contains(status))
			throw new InvalidJobStateException(
					"The files of the job must be PREPARING or FAILED in order to retry their preparation, but they are "
							+ status);

	}

	/**
	 * The failure message is free text, so it is cut to what the column holds.
	 */
	public static String limitFailure(String message) {

		if (message == null)
			return null;

		return message.length() > MAX_FAILURE_LENGTH ? message.substring(0, MAX_FAILURE_LENGTH) : message;

	}

}
