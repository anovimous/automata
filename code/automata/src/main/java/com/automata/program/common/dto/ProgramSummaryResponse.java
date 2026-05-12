package com.automata.program.common.dto;

import java.time.LocalDate;

import com.automata.program.common.enums.Platform;

import lombok.Builder;

@Builder
public record ProgramSummaryResponse(Long id, LocalDate insertionDate, String name, String link, Platform platform) {
}
