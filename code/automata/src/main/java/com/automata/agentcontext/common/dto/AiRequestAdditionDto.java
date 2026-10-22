package com.automata.agentcontext.common.dto;

import java.util.UUID;

// host is the name of the host. tenantId needs agentContextUuid, and must be one of
// the tenants of that context.
public record AiRequestAdditionDto(String requestBase64, String host, String base64Response, Long tenantId,
		UUID agentContextUuid) {

}
