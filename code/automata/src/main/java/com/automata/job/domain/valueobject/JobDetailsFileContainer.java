package com.automata.job.domain.valueobject;

import java.time.Instant;

import com.automata.job.domain.model.embedded.GenericConfig;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;

@Builder
public record JobDetailsFileContainer(Long jobId, Instant creationDate, ResultsVerbosity verbosity,
		GenericConfig genericConfig, JsonNode customConfig) {
}