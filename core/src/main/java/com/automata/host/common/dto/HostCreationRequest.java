package com.automata.host.common.dto;

import com.automata.host.common.enums.Scope;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HostCreationRequest(@NotBlank String host, @NotNull Scope scope, @NotNull Integer hostRateLimit,
		@NotNull Integer shortRateLimit, @NotNull Integer longRateLimit, @NotNull Long programId) {

}
