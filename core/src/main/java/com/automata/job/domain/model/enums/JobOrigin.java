package com.automata.job.domain.model.enums;

import java.util.UUID;

// Where a job comes from, seen from one agent context. It tells the agent whether a job is its
// own without handing it the identity of any other context.
public enum JobOrigin {

	THIS_CONTEXT, OTHER_CONTEXT, USER;

	public static JobOrigin of(JobCreator createdBy, UUID jobContextUuid, UUID contextUuid) {
		if (jobContextUuid != null && jobContextUuid.equals(contextUuid))
			return THIS_CONTEXT;
		return createdBy == JobCreator.USER ? USER : OTHER_CONTEXT;
	}

}
