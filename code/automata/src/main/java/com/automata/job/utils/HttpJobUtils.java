package com.automata.job.utils;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.automata.job.api.dto.HttpJobFilter;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;

import jakarta.persistence.criteria.Root;

public abstract class HttpJobUtils {

	public static Specification<HttpJob> buildSpecification(HttpJobFilter filter) {
		if (filter.hostId() != null) {
			return buildNarrowSpecification(filter);
		}

		return Specification.<HttpJob>unrestricted().and(hasProgramId(filter.programId()))
				.and(hasRoutineId(filter.routineId())).and(hasScope(filter.scope()))
				.and(hasCurrentStates(filter.currentStates()));
	}

	private static Specification<HttpJob> buildNarrowSpecification(HttpJobFilter filter) {
		return Specification.<HttpJob>unrestricted().and(hasProgramId(filter.programId()))
				.and(hasRoutineId(filter.routineId())).and(hasScope(filter.scope()))
				.and(hasCurrentStates(filter.currentStates())).and(hasHostId(filter.hostId()));
	}

	private static Specification<HttpJob> hasHostId(Long hostId) {
		return (root, query, cb) -> {
			if (hostId == null)
				return null;
			Root<NarrowHttpJob> narrowRoot = cb.treat(root, NarrowHttpJob.class);
			return cb.equal(narrowRoot.get("host").get("id"), hostId);
		};
	}

	private static Specification<HttpJob> hasProgramId(Long programId) {
		return (root, query, cb) -> programId == null ? null : cb.equal(root.get("program").get("id"), programId);
	}

	private static Specification<HttpJob> hasRoutineId(Long routineId) {
		return (root, query, cb) -> routineId == null ? null : cb.equal(root.get("routine").get("id"), routineId);
	}

	private static Specification<HttpJob> hasScope(HttpJobScope scope) {
		return (root, query, cb) -> scope == null ? null
				: cb.equal(root.get("genericDetails").get("httpJobScope"), scope);
	}

	private static Specification<HttpJob> hasCurrentStates(Set<JobState> currentStates) {
		return (root, query, cb) -> currentStates == null || currentStates.isEmpty() ? null
				: root.get("genericDetails").get("currentState").in(currentStates);
	}

}
