package com.automata.job.api.dto;

import com.automata.routine.common.enums.Duration;

public record NarrowHttpJobDetailsDto(Duration duration,

		Long hostId,

		Long tenantId) {

}
