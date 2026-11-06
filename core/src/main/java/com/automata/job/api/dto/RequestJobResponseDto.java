package com.automata.job.api.dto;

import org.springframework.lang.Nullable;

import com.automata.job.domain.model.enums.FailureCategory;
import com.automata.job.domain.model.enums.JobOrigin;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;

@Builder
public record RequestJobResponseDto(Long jobId, JobState currentState, @Nullable JobState requestedState,
		ResultsVerbosity verbosity, Integer priority, Integer rate, JsonNode customConfig, String routineKey,
		@Nullable String failureMessage, @Nullable FailureCategory failureCategory, @Nullable String aiDescription, @Nullable String aiResult,
		boolean approved, boolean terminal, boolean waitingForAction, JobOrigin origin) {

}
