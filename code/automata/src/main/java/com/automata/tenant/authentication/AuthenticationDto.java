package com.automata.tenant.authentication;

import java.time.LocalDate;

import lombok.Builder;

@Builder
public record AuthenticationDto(Long id, LocalDate creationDate, StaticAuthData authData,
		boolean isDynamicPopulationAvailable, DynamicCode code, Long tenantId) {

}
