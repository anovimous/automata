package com.automata.routine.common.dto;

import java.time.LocalDate;
import java.util.Set;

import com.automata.routine.common.enums.TargetCardinalityPair;

import lombok.Builder;

// The list view of a routine: it never carries usageDescriptionForAgent (only GET /api/routines/{id} does).
@Builder
public record RoutineSummaryResponseDto(Long id, LocalDate creationDate, String key, boolean isAvailableAtConsumer,
		boolean wordlistRequired, Set<TargetCardinalityPair> allowedTargets) {

}
