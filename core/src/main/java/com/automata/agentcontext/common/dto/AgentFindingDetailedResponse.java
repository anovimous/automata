package com.automata.agentcontext.common.dto;

import java.time.Instant;

import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingOrigin;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;

import lombok.Builder;

// A finding as the agent of a context sees it: it carries an origin and never the id of a context
@Builder
public record AgentFindingDetailedResponse(Long id, Instant creationDate, Long requestId, Long hostId, Long jobId,
		Long vulnerabilityId, double severity, FindingStatus status, double confidence, String evidence,
		String pocReproductionSteps, FindingCreator createdBy, FindingOrigin origin, String aiDescription,
		HumanVerdict humanVerdict, String humanComment, Instant reviewedAt) {

}
