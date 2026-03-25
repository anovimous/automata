package com.automata.job.common.dto;

import com.automata.routine.common.enums.Duration;

public record NarrowHttpJobDetailsDto(Duration duration,

		Long hostId,

		Long tenantId) {

}
