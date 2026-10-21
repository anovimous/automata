package com.automata.agentcontext.common.dto;

import com.automata.agentcontext.common.enums.AgentContextItemType;

public record AgentContextItemAddRequest(AgentContextItemType itemType, Long requestId, Long equalitySetId) {

}
