package com.automata.job.domain.valueobject;

import java.util.UUID;

import org.springframework.lang.Nullable;

import com.automata.job.domain.model.embedded.GenericConfig;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.ConfigFileStatus;
import com.automata.job.domain.model.enums.FailureCategory;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.fasterxml.jackson.databind.JsonNode;

public record HttpJobFullDetailsInternalDto(Long jobId, HttpJobScope httpJobScope, JobState currentState,
		@Nullable JobState requestedState, ResultsVerbosity verbosity, Integer priority, Integer rate,
		TargetSelector targetSelector, GenericConfig genericConfig, JsonNode customConfig, Long programId,
		String programName, Long routineId, String routineKey, @Nullable String failureMessage,
		@Nullable FailureCategory failureCategory, ConfigFileStatus configFileStatus, @Nullable String configFileFailure, @Nullable String aiDescription,
		@Nullable String aiResult, JobCreator createdBy, boolean approved, @Nullable UUID agentContextUuid) {

}
