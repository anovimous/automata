package com.automata.job.domain.model.enums;

import java.util.EnumSet;
import java.util.Set;

public enum JobState {
	DRAFT, TOQUEUE, SCHEDULED, QUEUED, RUNNING, PAUSED, CANCELED, FINISHED, FAILED, SELFTERMINATED, LOSTCONTACT;

	private static final Set<JobState> TERMINAL_STATES = EnumSet.of(FINISHED, FAILED, SELFTERMINATED, CANCELED,
			LOSTCONTACT);

	// A job in a terminal state never changes state again
	public boolean isTerminal() {
		return TERMINAL_STATES.contains(this);
	}
}
