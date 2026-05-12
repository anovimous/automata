package com.automata.job.api.dto;

import java.util.Set;

import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;

public record HttpJobFilter(Long programId, Long hostId, Long routineId, HttpJobScope scope,
		Set<JobState> currentStates) {

}
