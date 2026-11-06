package com.automata.request.common.dto;

import com.automata.request.common.enums.Source;

import jakarta.validation.constraints.NotNull;

public record RawRequestAdditionDto(@NotNull String requestBase64, @NotNull Source source, @NotNull Long hostId, Long tenantId,
		String responseBase64) {

}
