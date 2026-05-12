package com.automata.job.domain.valueobject;

import java.util.Set;

import com.automata.job.domain.model.embedded.GenericConfig;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.job.domain.model.enums.TargetType;
import com.automata.tenant.authentication.StaticAuthData;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;

@Builder
public record JobDetailsFileContainer(Long jobId, String routineKey, Integer rate, ResultsVerbosity verbosity,
		Set<String> wordlistsPaths, StaticAuthData auth, TargetType targetType, GenericConfig genericConfig,
		JsonNode customConfig) {
}