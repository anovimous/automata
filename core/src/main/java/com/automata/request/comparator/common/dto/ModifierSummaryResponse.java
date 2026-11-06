package com.automata.request.comparator.common.dto;

import java.time.LocalDate;

import com.automata.request.comparator.hash.HashTarget;

import lombok.Builder;

@Builder
public record ModifierSummaryResponse(Long id, LocalDate creationDate, HashTarget target) {

}
