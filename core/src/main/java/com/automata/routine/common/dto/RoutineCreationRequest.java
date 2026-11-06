package com.automata.routine.common.dto;

import java.util.Set;

import com.automata.routine.common.enums.Overhead;
import com.automata.routine.common.enums.Protocol;
import com.automata.routine.common.enums.ResultContentType;
import com.automata.routine.common.enums.TargetCardinalityPair;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoutineCreationRequest(@NotBlank String key, String description, Boolean isAvailableAtConsumer, Boolean wordlistRequired, Overhead overhead,
		@NotNull Protocol protocol, @NotNull Set<TargetCardinalityPair> allowedTargets, Long vulnerabilityId, JsonNode configSchema,
		@NotNull ResultContentType resultContentType) {

}
