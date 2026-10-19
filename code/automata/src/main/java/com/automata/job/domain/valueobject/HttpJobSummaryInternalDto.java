package com.automata.job.domain.valueobject;

import java.time.Instant;

import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;

public record HttpJobSummaryInternalDto(Long id, Instant creationDate, HttpJobScope scope, JobState currentState,
		Integer priority, Integer rate, Long routineId, String routineKey) {

	public static HttpJobSummaryInternalDto of(HttpJob job) {
		HttpJobGenericDetails details = job.getGenericDetails();
		return new HttpJobSummaryInternalDto(job.getId(), job.getCreationDate(), details.getHttpJobScope(),
				details.getCurrentState(), details.getPriority(), details.getRate(), job.getRoutine().getId(),
				job.getRoutine().getKey());
	}

}
