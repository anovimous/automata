package com.automata.job.domain.valueobject;

import java.time.Instant;

import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;

public record HttpJobSummaryInternalDto(Long id, Instant creationDate, HttpJobScope scope, JobState currentState,
		Integer priority, Integer rate, Long routineId, String routineKey) {

}
