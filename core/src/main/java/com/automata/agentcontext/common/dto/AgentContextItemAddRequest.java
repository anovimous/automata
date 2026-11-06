package com.automata.agentcontext.common.dto;

import com.automata.agentcontext.common.enums.AgentContextItemType;

import jakarta.validation.constraints.NotNull;

public record AgentContextItemAddRequest(@NotNull AgentContextItemType itemType, Long requestId, Long equalitySetId) {

}
