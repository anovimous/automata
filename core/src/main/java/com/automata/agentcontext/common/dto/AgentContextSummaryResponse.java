package com.automata.agentcontext.common.dto;

import java.time.Instant;
import java.util.UUID;

import com.automata.agentcontext.common.enums.RepresentativeStrategy;

import lombok.Builder;

@Builder
public record AgentContextSummaryResponse(Long id, UUID uuid, String name, Long hostId,
		RepresentativeStrategy representativeStrategy, int itemCount, Instant creationDate) {

}
