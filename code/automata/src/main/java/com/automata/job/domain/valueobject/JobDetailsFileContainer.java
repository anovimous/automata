package com.automata.job.domain.valueobject;

import java.time.Instant;

import com.automata.job.domain.model.embedded.GenericConfig;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JobDetailsFileContainer {

	private Long jobId;

	private Instant creationDate;

	private ResultsVerbosity verbosity;

	private GenericConfig genericConfig;

	private JsonNode customConfig;

}
