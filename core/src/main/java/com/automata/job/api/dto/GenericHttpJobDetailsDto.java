package com.automata.job.api.dto;

import java.util.List;
import java.util.Set;

import com.automata.job.domain.model.embedded.MatchAndReplace;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record GenericHttpJobDetailsDto(@NotNull ResultsVerbosity verbosity, Integer priority, Integer rate,
		@NotNull Long routineId, Set<Long> wordlistsIds, @NotNull @Valid TargetSelector targetSelector,
		List<MatchAndReplace> matchAndReplace, JsonNode customConfig) {
}
