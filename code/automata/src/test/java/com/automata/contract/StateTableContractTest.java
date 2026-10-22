package com.automata.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.policy.JobStateTransitions;
import com.automata.job.domain.policy.JobStateTransitions.ToApplyJobStateChange;

/**
 * The tables of docs/worker-contract.md are checked against
 * {@link JobStateTransitions}: every row is computed from the code by trying all
 * the states, and must be the same as the rows of the document.
 */
class StateTableContractTest {

	private static final Path DOC = Path.of("docs", "worker-contract.md");

	/** The rows between the two markers of a table, without the header. */
	private static List<String> rowsOf(String table) throws IOException {
		List<String> lines = Files.readAllLines(DOC);
		int start = lines.indexOf("<!-- " + table + ":start -->");
		int end = lines.indexOf("<!-- " + table + ":end -->");
		if (start < 0 || end < start)
			throw new IllegalStateException("The document has no table " + table);

		List<String> rows = new ArrayList<>();
		for (String line : lines.subList(start + 1, end)) {
			if (!line.startsWith("|") || line.startsWith("| From") || line.startsWith("| Action") || line.startsWith("|---"))
				continue;
			rows.add(String.join("|", java.util.Arrays.stream(line.split("\\|")).map(String::trim)
					.filter(cell -> !cell.isEmpty()).toList()));
		}
		return rows;
	}

	private static boolean accepted(JobState current, JobState requested, JobState reported) {
		try {
			JobStateTransitions.reportStateAppliedByWorker(current, requested, reported);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** from|to|yes when the user must have requested it, else no */
	static List<String> workerReports() {
		List<String> rows = new ArrayList<>();
		for (JobState from : JobState.values())
			for (JobState to : JobState.values()) {
				// a repeated report of the same state is a retry, it is not a move
				if (from == to)
					continue;
				boolean withoutRequest = accepted(from, null, to);
				boolean withRequest = accepted(from, to, to);
				if (withoutRequest || withRequest)
					rows.add(from + "|" + to + "|" + (withoutRequest ? "no" : "yes"));
			}
		return rows;
	}

	/** action|from|current becomes|requested becomes (for no requested state before) */
	static List<String> userAndCoreActions() {
		List<String> rows = new ArrayList<>();
		List<Object[]> actions = List.of(new Object[] { "prepare", (BiFunction<JobState, JobState, ToApplyJobStateChange>) JobStateTransitions::prepareForQueueing },
				new Object[] { "pause", (BiFunction<JobState, JobState, ToApplyJobStateChange>) JobStateTransitions::requestPause },
				new Object[] { "resume", (BiFunction<JobState, JobState, ToApplyJobStateChange>) JobStateTransitions::requestResume },
				new Object[] { "cancel", (BiFunction<JobState, JobState, ToApplyJobStateChange>) JobStateTransitions::cancel },
				new Object[] { "lose contact", (BiFunction<JobState, JobState, ToApplyJobStateChange>) JobStateTransitions::loseContact });
		for (Object[] action : actions) {
			@SuppressWarnings("unchecked")
			BiFunction<JobState, JobState, ToApplyJobStateChange> change = (BiFunction<JobState, JobState, ToApplyJobStateChange>) action[1];
			for (JobState from : JobState.values()) {
				try {
					ToApplyJobStateChange result = change.apply(from, null);
					rows.add(action[0] + "|" + from + "|" + result.toApplyCurrentState() + "|"
							+ (result.toApplyRequestedState() == null ? "-" : result.toApplyRequestedState()));
				} catch (RuntimeException e) {
					// not allowed from this state
				}
			}
		}
		return rows;
	}

	@Test
	void workerReportsTableIsTheSameAsTheCode() throws IOException {
		assertEquals(workerReports(), rowsOf("worker-reports"));
	}

	@Test
	void actionsTableIsTheSameAsTheCode() throws IOException {
		assertEquals(userAndCoreActions(), rowsOf("actions"));
	}

	@Test
	void everyStateIsInTheStatesList() throws IOException {
		List<String> documented = Files.readAllLines(DOC).stream().filter(l -> l.startsWith("- `") && l.contains("` — "))
				.map(l -> l.substring(3, l.indexOf('`', 3))).toList();

		assertEquals(java.util.Arrays.stream(JobState.values()).map(Enum::name).toList(), documented);
	}

}
