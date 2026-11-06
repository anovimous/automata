package com.automata.agentcontext.common.dto;

import java.util.List;

import com.automata.agentcontext.common.enums.RepresentativeStrategy;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.routine.common.enums.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentContextCreationRequest(@NotBlank String name, String description, @NotNull Long hostId,
		boolean defaultAutoApproval, @NotNull RepresentativeStrategy representativeStrategy, List<Long> allowedRoutineIds,
		Duration defaultDuration, ResultsVerbosity defaultVerbosity, Integer defaultPriority, Integer maxRate,
		Integer defaultJobRate, List<Long> tenantIds) {

}
