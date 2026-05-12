package com.automata.routine.common.dto;

import java.util.Optional;
import java.util.Set;

import com.automata.routine.common.enums.Overhead;
import com.automata.routine.common.enums.Protocol;
import com.automata.routine.common.enums.TargetCardinalityPair;

public record RoutinePatchRequest(Optional<String> description, boolean setDescriptionNull,
		Optional<Boolean> isAvailableAtConsumer, Optional<Overhead> overhead, Optional<Protocol> protocol,
		Set<TargetCardinalityPair> allowedTargets, Optional<Long> vulnerabilityId, boolean setVulnerabilityNull) {

}
