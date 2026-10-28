package com.automata.agentcontext.common.dto;

import jakarta.validation.constraints.NotNull;

public record AgentContextTenantAddRequest(@NotNull Long tenantId) {

}
