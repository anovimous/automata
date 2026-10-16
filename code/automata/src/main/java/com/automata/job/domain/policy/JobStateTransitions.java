package com.automata.job.domain.policy;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * <li>A worker reports the current state, which clears the requested one, and
 * only as a move the table of what a worker may report allows. Pausing,
 * canceling and resuming are also reported only when the user requested them.</li>
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

	// What a worker may report, by the current state of the job. The jobs which no
	// worker holds, and the ended ones, are not in it, so nothing can be reported
	// for them.
	private static final Map<JobState, Set<JobState>> REPORTABLE_BY_WORKER = Map.of(
			JobState.QUEUED, EnumSet.of(JobState.RUNNING, JobState.CANCELED, JobState.FAILED, JobState.TERMINATED),
			JobState.RUNNING, EnumSet.of(JobState.PAUSED, JobState.FINISHED, JobState.FAILED, JobState.TERMINATED),
			JobState.PAUSED, EnumSet.of(JobState.RUNNING, JobState.CANCELED, JobState.TERMINATED));

	// The states which a worker reports only when the user has requested them
	private static final Set<JobState> REPORTABLE_ONLY_WHEN_REQUESTED = EnumSet.of(JobState.PAUSED,
			JobState.CANCELED);

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

		if (!REPORTABLE_BY_WORKER.getOrDefault(current, Set.of()).contains(reported))
			throw new InvalidJobStateException(
					String.format("A worker can't report the state %s for a job which is %s", reported, current));

		// the user decides on pausing and canceling, the worker only carries it out
		if (isReportedOnlyWhenRequested(current, reported) && requested != reported)
			throw new InvalidJobStateException(
					String.format("A worker can only report the state %s for a job for which it was requested, but %s",
							reported, requested == null ? "nothing is requested" : requested + " is requested"));

		return new ToApplyJobStateChange(reported, null);
	}

	// Pausing and canceling are always requested by the user. So is resuming, but
	// a worker starts a queued job on its own.
	private static boolean isReportedOnlyWhenRequested(JobState current, JobState reported) {
		return REPORTABLE_ONLY_WHEN_REQUESTED.contains(reported)
				|| (current == JobState.PAUSED && reported == JobState.RUNNING);
	}

}
