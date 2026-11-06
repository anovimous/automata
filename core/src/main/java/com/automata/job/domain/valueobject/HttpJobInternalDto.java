package com.automata.job.domain.valueobject;

import java.time.Instant;

import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.routine.common.enums.Duration;

public record HttpJobInternalDto(Long id, Instant creationDate, HttpJobScope scope, Duration duration, Integer priority,
		Integer rate, Long programId, Long hostId) {

}
