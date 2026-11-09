package com.automata.routine.common.dto;

import java.util.Optional;
import java.util.Set;

import com.automata.routine.common.enums.TargetCardinalityPair;
import com.fasterxml.jackson.databind.JsonNode;

// description and usageDescriptionForAgent are mandatory: they can be changed but not removed (a blank value is a 400).
public record RoutinePatchRequest(Optional<String> description, Optional<String> usageDescriptionForAgent,
		Optional<Boolean> isAvailableAtConsumer, Optional<Boolean> wordlistRequired,
		Set<TargetCardinalityPair> allowedTargets, Optional<Long> vulnerabilityId, boolean setVulnerabilityNull,
		Optional<JsonNode> configSchema, boolean setConfigSchemaNull) {

}
