package com.automata.routine.common.dto;

import java.time.LocalDate;
import java.util.Set;

import com.automata.routine.common.enums.Overhead;
import com.automata.routine.common.enums.Protocol;
import com.automata.routine.common.enums.TargetCardinalityPair;

import lombok.Builder;

@Builder
public record RoutineSummaryResponseDto(Long id, LocalDate creationDate, String key, boolean isAvailableAtConsumer,
		Overhead overhead, Protocol protocol, Set<TargetCardinalityPair> allowedTargets) {

}
