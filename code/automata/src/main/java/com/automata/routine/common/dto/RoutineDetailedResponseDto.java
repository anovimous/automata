package com.automata.routine.common.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import com.automata.routine.common.enums.Overhead;
import com.automata.routine.common.enums.Protocol;
import com.automata.routine.common.enums.TargetCardinalityPair;

import lombok.Builder;

@Builder
public record RoutineDetailedResponseDto(Long id, LocalDate creationDate, String key, String description,
		Instant updatedAt, boolean isAvailableAtConsumer, Overhead overhead, Protocol protocol,
		Set<TargetCardinalityPair> allowedTargets, Long vulnerabilityId) {

}
