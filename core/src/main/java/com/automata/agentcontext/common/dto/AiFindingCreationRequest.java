package com.automata.agentcontext.common.dto;

import com.automata.finding.common.enums.FindingStatus;

// The body the agent sends to create a finding. The creator and the context come from the route.
public record AiFindingCreationRequest(Long requestId, Long jobId, Long vulnerabilityId, Double severity,
		FindingStatus status, Double confidence, String evidence, String pocReproductionSteps,
		String aiDescription) {

}
