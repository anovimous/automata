package com.automata.agentcontext.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.automata.agentcontext.common.enums.RepresentativeStrategy;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.routine.common.enums.Duration;

import lombok.Builder;

@Builder
public record AgentContextDetailedResponse(Long id, UUID uuid, String name, String description, Long hostId,
		boolean defaultAutoApproval, RepresentativeStrategy representativeStrategy, List<Long> allowedRoutineIds,
		Duration defaultDuration, ResultsVerbosity defaultVerbosity, Integer defaultPriority, Integer maxRate,
		Instant creationDate, List<AgentContextItemResponse> items) {

}
