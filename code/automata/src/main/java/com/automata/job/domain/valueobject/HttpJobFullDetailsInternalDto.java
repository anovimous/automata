package com.automata.job.domain.valueobject;

import org.springframework.lang.Nullable;

import com.automata.job.domain.model.embedded.GenericConfig;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.fasterxml.jackson.databind.JsonNode;

public record HttpJobFullDetailsInternalDto(Long jobId, HttpJobScope httpJobScope, JobState currentState,
		@Nullable JobState requestedState, ResultsVerbosity verbosity, Integer priority, Integer rate,
		TargetSelector targetSelector, GenericConfig genericConfig, JsonNode customConfig, Long programId,
		String programName, Long routineId, String routineKey) {

}
