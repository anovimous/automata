package com.automata.job.api.dto;

import com.automata.routine.common.enums.Duration;

import jakarta.validation.constraints.NotNull;

public record NarrowHttpJobDetailsDto(@NotNull Duration duration,

		@NotNull Long hostId,

		Long tenantId) {

}
