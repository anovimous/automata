package com.automata.agentcontext.common.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// host is the name of the host. tenantId needs agentContextUuid, and must be one of
// the tenants of that context.
public record AiRequestAdditionDto(@NotNull String requestBase64, @NotBlank String host, String base64Response, Long tenantId,
		UUID agentContextUuid) {

}
