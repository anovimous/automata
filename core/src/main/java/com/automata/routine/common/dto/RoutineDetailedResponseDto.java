package com.automata.routine.common.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import com.automata.routine.common.enums.ResultContentType;
import com.automata.routine.common.enums.TargetCardinalityPair;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;

@Builder
public record RoutineDetailedResponseDto(Long id, LocalDate creationDate, String key, String description,
		String usageDescriptionForAgent, Instant updatedAt, boolean isAvailableAtConsumer, boolean wordlistRequired,
		Set<TargetCardinalityPair> allowedTargets, Long vulnerabilityId, JsonNode configSchema,
		ResultContentType resultContentType) {

}
