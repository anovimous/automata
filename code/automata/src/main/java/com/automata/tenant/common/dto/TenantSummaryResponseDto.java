package com.automata.tenant.common.dto;

import java.time.LocalDate;

import lombok.Builder;

@Builder
public record TenantSummaryResponseDto(Long id, LocalDate creationDate, String name) {

}
