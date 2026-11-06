package com.automata.tenant;

import java.time.LocalDate;

import lombok.Builder;

@Builder
public record TenantDetailedResponseDto(Long id, LocalDate creationDate, String name, String email, Long hostId) {

}
