package com.automata.agentcontext.common.dto;

import java.util.List;

import com.automata.agentcontext.common.enums.RepresentativeStrategy;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.routine.common.enums.Duration;

public record AgentContextCreationRequest(String name, String description, Long hostId,
		boolean defaultAutoApproval, RepresentativeStrategy representativeStrategy, List<Long> allowedRoutineIds,
		Duration defaultDuration, ResultsVerbosity defaultVerbosity, Integer defaultPriority, Integer maxRate) {

}
