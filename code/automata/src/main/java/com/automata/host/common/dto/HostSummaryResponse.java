package com.automata.host.common.dto;

import java.time.LocalDate;

import com.automata.host.common.enums.Scope;

import lombok.Builder;

@Builder
public record HostSummaryResponse(Long id, LocalDate insertionDate, String host, Integer level, Scope scope,
		boolean outOfScope) {

}
