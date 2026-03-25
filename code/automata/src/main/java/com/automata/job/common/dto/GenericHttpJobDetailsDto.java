package com.automata.job.common.dto;

import com.automata.job.common.enums.ResultsVerbosity;
import com.automata.job.matchandreplace.MatchAndReplace;
import com.automata.job.selector.TargetSelector;
import com.fasterxml.jackson.databind.JsonNode;

public record GenericHttpJobDetailsDto(ResultsVerbosity verbosity, Integer priority, Integer rate, Long routineId,
		TargetSelector targetSelector, MatchAndReplace matchAndReplace, JsonNode customConfig) {
}
