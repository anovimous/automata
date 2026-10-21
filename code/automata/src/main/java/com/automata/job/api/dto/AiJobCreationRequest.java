package com.automata.job.api.dto;

import java.util.Set;

import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.routine.common.enums.Duration;
import com.fasterxml.jackson.databind.JsonNode;

public record AiJobCreationRequest(Long requestId, String aiDescription, Long routineId, Duration duration,
		Set<Long> wordlistsIds, Integer rate, ResultsVerbosity verbosity, Integer priority, JsonNode customConfig) {

}
