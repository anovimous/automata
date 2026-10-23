package com.automata.job.domain.valueobject;

import java.time.Instant;
import java.util.UUID;

import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobOrigin;
import com.automata.job.domain.model.enums.JobState;

// A job seen from the history of a request of one agent context: no identity of the context
// that owns it, only where it comes from relative to the asking context.
public record RequestJobSummaryInternalDto(Long id, Instant creationDate, HttpJobScope scope, JobState currentState,
		Integer priority, Integer rate, String routineKey, boolean approved, JobOrigin origin) {

	public static RequestJobSummaryInternalDto of(HttpJob job, UUID contextUuid) {
		HttpJobGenericDetails details = job.getGenericDetails();
		UUID jobContextUuid = job.getAgentContext() == null ? null : job.getAgentContext().getUuid();
		return new RequestJobSummaryInternalDto(job.getId(), job.getCreationDate(), details.getHttpJobScope(),
				details.getCurrentState(), details.getPriority(), details.getRate(), job.getRoutine().getKey(),
				job.isApproved(), JobOrigin.of(job.getCreatedBy(), jobContextUuid, contextUuid));
	}

}
