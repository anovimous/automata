package com.automata.finding.common.enums;

import java.util.UUID;

// Where a finding comes from, seen from one agent context. It tells the agent whether a finding is
// its own without handing it the identity of any other context.
public enum FindingOrigin {

	THIS_CONTEXT, OTHER_CONTEXT, USER;

	public static FindingOrigin of(FindingCreator createdBy, UUID findingContextUuid, UUID contextUuid) {
		if (findingContextUuid != null && findingContextUuid.equals(contextUuid))
			return THIS_CONTEXT;
		return createdBy == FindingCreator.USER ? USER : OTHER_CONTEXT;
	}

}
