package com.automata.agentcontext.common.dto;

import java.time.Instant;

import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingOrigin;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;

import lombok.Builder;

// A finding as the agent of a context sees it: it carries an origin and never the id of a context
@Builder
public record AgentFindingSummaryResponse(Long id, Long requestId, Long hostId, Long vulnerabilityId,
		double severity, FindingStatus status, double confidence, FindingCreator createdBy, FindingOrigin origin,
		HumanVerdict humanVerdict, Instant creationDate) {

}
