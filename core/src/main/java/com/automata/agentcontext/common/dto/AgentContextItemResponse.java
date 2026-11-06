package com.automata.agentcontext.common.dto;

import com.automata.agentcontext.common.enums.AgentContextItemType;

import lombok.Builder;

@Builder
public record AgentContextItemResponse(Long id, AgentContextItemType itemType, Long requestId, Long equalitySetId) {

}
