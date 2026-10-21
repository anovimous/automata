package com.automata.agentcontext.common.dto;

import java.util.List;
import java.util.Optional;

import com.automata.agentcontext.common.enums.RepresentativeStrategy;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.routine.common.enums.Duration;

public record AgentContextPatchRequest(Optional<String> name, Optional<String> description,
		boolean setDescriptionNull, Optional<Boolean> defaultAutoApproval,
		Optional<RepresentativeStrategy> representativeStrategy, List<Long> allowedRoutineIds,
		Optional<Duration> defaultDuration, boolean setDefaultDurationNull,
		Optional<ResultsVerbosity> defaultVerbosity, boolean setDefaultVerbosityNull,
		Optional<Integer> defaultPriority, boolean setDefaultPriorityNull, Optional<Integer> maxRate,
		boolean setMaxRateNull) {

}
