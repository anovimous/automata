package com.automata.program.common.dto;

import java.time.LocalDate;
import java.util.Set;

import com.automata.program.common.enums.Platform;

import lombok.Builder;

@Builder
public record ProgramDetailedResponse(Long id, LocalDate insertionDate, String name, String link,
		Integer programRateLimit, Platform platform, Set<Long> outOfScopeVulnIds) {
}