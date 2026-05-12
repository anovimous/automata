package com.automata.job.domain.valueobject;

import java.time.Instant;

import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.routine.common.enums.Duration;

public record HttpJobInternalDto(Long id, Instant creationDate, HttpJobScope scope, Duration duration, Integer priority,
		Integer rate, Long programId, Long hostId) implements Comparable<HttpJobInternalDto> {

	@Override
	public int compareTo(HttpJobInternalDto other) {
		if (this.priority() >= 4 || other.priority() >= 4)
			return Integer.compare(this.priority(), other.priority());

		else {
			if (this.programId() == other.programId())
				return Integer.compare(this.priority(), other.priority());
			else
				return this.creationDate().compareTo(other.creationDate());
		}
	}

}
