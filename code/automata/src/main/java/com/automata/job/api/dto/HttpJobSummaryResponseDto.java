package com.automata.job.api.dto;

import java.time.Instant;

import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;

import lombok.Builder;

@Builder
public record HttpJobSummaryResponseDto(Long id, Instant creationDate, HttpJobScope scope, JobState currentState,
		Integer priority, Integer rate, Long routineId, String routineKey) {

}
