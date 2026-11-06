package com.automata.agentcontext.common.dto;

import java.util.List;

import com.automata.tenant.authentication.StaticAuthData;

import lombok.Builder;

// A tenant of the context with its authentication (null when it has none) and the
// requests of the context that belong to it
@Builder
public record AgentContextTenantResponse(Long tenantId, String name, String email, StaticAuthData authData,
		List<Long> requestIds) {

}
