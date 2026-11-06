package com.automata.job.domain.policy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.automata.job.domain.valueobject.HttpJobInternalDto;

/**
 * Decides in which order the jobs waiting to be queued are handled. A higher
 * priority is more important and is handled first.
 * <ul>
 * <li>Eager jobs (priority 4 or higher) are handled before all others, across
 * all programs.</li>
 * <li>The other jobs of a program are handled by priority, then by age.</li>
 * <li>Different programs are interleaved by the age of the next job each of
 * them has.</li>
 * </ul>
 * The order is built as a sequence instead of being a pairwise comparison, since
 * "by priority inside a program, by age between programs" is not a consistent
 * comparison of two jobs (it can contain a cycle).
 */
public final class JobDispatchOrder {

	private static final int MIN_EAGER_PRIORITY = 4;

	// Only the same job compares as equal, so no job is ever considered a duplicate.
	private static final Comparator<HttpJobInternalDto> BY_PRIORITY_THEN_AGE = Comparator
			.comparing(HttpJobInternalDto::priority, Comparator.reverseOrder())
			.thenComparing(HttpJobInternalDto::creationDate).thenComparing(HttpJobInternalDto::id);

	private static final Comparator<Deque<HttpJobInternalDto>> BY_AGE_OF_NEXT_JOB = Comparator
			.comparing((Deque<HttpJobInternalDto> queue) -> queue.peekFirst().creationDate())
			.thenComparing(queue -> queue.peekFirst().id());

	private JobDispatchOrder() {
	}

	public static boolean isEager(int priority) {
		return priority >= MIN_EAGER_PRIORITY;
	}

	public static List<HttpJobInternalDto> sort(Collection<HttpJobInternalDto> jobs) {

		List<HttpJobInternalDto> sorted = new ArrayList<>(jobs.size());

		jobs.stream().filter(job -> isEager(job.priority())).sorted(BY_PRIORITY_THEN_AGE).forEach(sorted::add);

		Map<Long, List<HttpJobInternalDto>> lazyJobsByProgram = new HashMap<>();
		jobs.stream().filter(job -> !isEager(job.priority()))
				.forEach(job -> lazyJobsByProgram.computeIfAbsent(job.programId(), id -> new ArrayList<>()).add(job));

		PriorityQueue<Deque<HttpJobInternalDto>> programQueues = new PriorityQueue<>(BY_AGE_OF_NEXT_JOB);
		lazyJobsByProgram.values().forEach(programJobs -> {
			programJobs.sort(BY_PRIORITY_THEN_AGE);
			programQueues.add(new ArrayDeque<>(programJobs));
		});

		while (!programQueues.isEmpty()) {
			Deque<HttpJobInternalDto> programQueue = programQueues.poll();
			sorted.add(programQueue.pollFirst());
			if (!programQueue.isEmpty())
				programQueues.add(programQueue);
		}

		return sorted;

	}

}
