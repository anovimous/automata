package com.automata.routine.common.dto;

import java.util.Set;

import com.automata.routine.common.enums.Overhead;
import com.automata.routine.common.enums.Protocol;
import com.automata.routine.common.enums.TargetCardinalityPair;

public record RoutineCreationRequest(String key, String description, Boolean isAvailableAtConsumer, Overhead overhead,
		Protocol protocol, Set<TargetCardinalityPair> allowedTargets, Long vulnerabilityId) {

}
