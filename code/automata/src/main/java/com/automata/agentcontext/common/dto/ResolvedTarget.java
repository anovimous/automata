package com.automata.agentcontext.common.dto;

import com.automata.agentcontext.common.enums.AgentContextItemType;

import lombok.Builder;

@Builder
public record ResolvedTarget(Long requestId, boolean automataJobEligible, boolean autoApproval,
		AgentContextItemType sourceItemType, Long sourceEqualitySetId) {

}
