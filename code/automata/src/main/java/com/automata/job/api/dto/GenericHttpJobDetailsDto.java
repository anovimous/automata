package com.automata.job.api.dto;

import java.util.List;
import java.util.Set;

import com.automata.job.domain.model.embedded.MatchAndReplace;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.fasterxml.jackson.databind.JsonNode;

public record GenericHttpJobDetailsDto(ResultsVerbosity verbosity, Integer priority, Integer rate, Long routineId,
		Set<Long> wordlistsIds, TargetSelector targetSelector, List<MatchAndReplace> matchAndReplace,
		JsonNode customConfig) {
}
