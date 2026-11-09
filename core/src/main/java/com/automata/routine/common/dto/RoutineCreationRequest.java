package com.automata.routine.common.dto;

import java.util.Set;

import com.automata.routine.Routine;
import com.automata.routine.common.enums.ResultContentType;
import com.automata.routine.common.enums.TargetCardinalityPair;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoutineCreationRequest(@NotBlank String key, @NotBlank String description,
		@NotBlank @Size(max = Routine.MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH) String usageDescriptionForAgent,
		Boolean isAvailableAtConsumer, Boolean wordlistRequired, @NotNull Set<TargetCardinalityPair> allowedTargets,
		Long vulnerabilityId, JsonNode configSchema, @NotNull ResultContentType resultContentType) {

}
