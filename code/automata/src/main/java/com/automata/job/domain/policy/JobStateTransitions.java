package com.automata.job.domain.policy;

import java.util.List;

import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.model.enums.JobState;

/**
 * Decides which changes of a job's state are allowed, depending on who asks for
 * them. A job has two states: the current one, and the requested one which is a
 * pending wish of the user that the worker reads when it polls.
 * <ul>
 * <li>A user changes the current state directly only while no worker holds the
 * job (prepare, and canceling a job which is still to be queued). Otherwise the
 * user only sets the requested state.</li>
 * <li>A worker reports the current state, which clears the requested one.</li>
 * </ul>
 * Each method checks the change and returns the new pair of states, or throws
 * {@link InvalidJobStateException}. It never modifies anything itself.
 */
public final class JobStateTransitions {

	// The states which only the central server can put a job in, not the worker
	private static final List<JobState> NOT_REPORTABLE_BY_WORKER = List.of(JobState.DRAFT, JobState.TOQUEUE,
			JobState.SCHEDULED, JobState.QUEUED);

	private static final List<JobState> CANCELABLE = List.of(JobState.TOQUEUE, JobState.SCHEDULED, JobState.QUEUED,
			JobState.PAUSED);

	// The worker holds these jobs, so canceling them is only requested
	private static final List<JobState> CANCELABLE_BY_REQUEST = List.of(JobState.QUEUED, JobState.PAUSED);

	/**
	 * The new current and requested states of a job.
	 */
	public record ToApplyJobStateChange(JobState toApplyCurrentState, JobState toApplyRequestedState) {
	}

	private JobStateTransitions() {
	}

	public static ToApplyJobStateChange prepareForQueueing(JobState current, JobState requested) {

		if (current != JobState.DRAFT)
			throw InvalidJobStateException.requiredState(JobState.DRAFT, current, "prepare it for queueing");

		return new ToApplyJobStateChange(JobState.TOQUEUE, requested);
	}

	public static ToApplyJobStateChange requestPause(JobState current, JobState requested) {

		if (current != JobState.RUNNING)
			throw InvalidJobStateException.requiredState(JobState.RUNNING, current, "pause it");

		return new ToApplyJobStateChange(current, JobState.PAUSED);
	}

	public static ToApplyJobStateChange requestResume(JobState current, JobState requested) {

		if (current != JobState.PAUSED)
			throw InvalidJobStateException.requiredState(JobState.PAUSED, current, "resume it");

		return new ToApplyJobStateChange(current, JobState.RUNNING);
	}

	public static ToApplyJobStateChange cancel(JobState current, JobState requested) {

		if (current == JobState.SCHEDULED)
			throw new UnsupportedOperationException("Canceling scheduled jobs is not implemented yet");

		if (!CANCELABLE.contains(current))
			throw new InvalidJobStateException(
					"The job must be in the [TOQUEUE,SCHEDULED,QUEUED,PAUSED] states in order to cancel it, but it is "
							+ current);

		if (CANCELABLE_BY_REQUEST.contains(current))
			return new ToApplyJobStateChange(current, JobState.CANCELED);

		return new ToApplyJobStateChange(JobState.CANCELED, requested);
	}

	public static ToApplyJobStateChange reportStateAppliedByWorker(JobState current, JobState requested, JobState reported) {

		if (NOT_REPORTABLE_BY_WORKER.contains(reported))
			throw new InvalidJobStateException("The job is not allowed to this state by the orchestrator");

		return new ToApplyJobStateChange(reported, null);
	}

}
