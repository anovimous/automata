package com.automata.job.api.dto;

import org.springframework.lang.Nullable;

import com.automata.job.domain.model.embedded.GenericConfig;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.program.common.dto.ProgramSummaryDetailsResponseDto;
import com.automata.routine.common.dto.RoutineSummaryDetailsResponseDto;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;

@Builder
public record HttpJobResponseDto(Long jobId, HttpJobScope httpJobScope, JobState currentState,
		@Nullable JobState requestedState, ResultsVerbosity verbosity, Integer priority, Integer rate,
		TargetSelector targetSelector, GenericConfig genericConfig, JsonNode customConfig,
		ProgramSummaryDetailsResponseDto program, RoutineSummaryDetailsResponseDto routine) {

}
