package com.automata.job.domain.model.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class JobStateTest {

	@Test
	void theTerminalStatesAreTheEndedOnes() {
		Set<JobState> terminal = EnumSet.noneOf(JobState.class);
		for (JobState state : JobState.values())
			if (state.isTerminal())
				terminal.add(state);

		assertEquals(EnumSet.of(JobState.FINISHED, JobState.FAILED, JobState.SELFTERMINATED, JobState.CANCELED,
				JobState.LOSTCONTACT), terminal);
	}

}
