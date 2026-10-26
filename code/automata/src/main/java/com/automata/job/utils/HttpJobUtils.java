package com.automata.job.utils;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.automata.job.api.dto.HttpJobFilter;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.NarrowJobTargetRequest;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.request.Request;
import com.automata.request.common.enums.RequestContentType;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public abstract class HttpJobUtils {

	// A request can be a job target when it can be rebuilt from its detailed components: it has
	// no body at all, or its body is JSON or FORM (the only bodies with BodyProperty rows)
	public static boolean isJobEligible(RequestContentType contentType, long bodySizeBytes) {
		if (bodySizeBytes == 0)
			return true;
		return contentType == RequestContentType.JSON || contentType == RequestContentType.FORM;
	}

	public static boolean isJobEligible(Request request) {
		return isJobEligible(request.getContentType(), request.getBodySizeBytes());
	}

	public static void requireJobEligible(Request request) {
		if (!isJobEligible(request))
			throw IneligibleJobTargetException.forRequest(request.getId(), request.getContentType());
	}

	public static Specification<HttpJob> buildSpecification(HttpJobFilter filter) {
		if (filter.hostId() != null) {
			return buildNarrowSpecification(filter);
		}

		return Specification.<HttpJob>unrestricted().and(hasProgramId(filter.programId()))
				.and(hasRoutineId(filter.routineId())).and(hasScope(filter.scope()))
				.and(hasCurrentStates(filter.currentStates())).and(hasCreatedBy(filter.createdBy()))
				.and(hasAgentContextId(filter.agentContextId())).and(hasTargetRequestId(filter.targetRequestId()));
	}

	private static Specification<HttpJob> buildNarrowSpecification(HttpJobFilter filter) {
		return Specification.<HttpJob>unrestricted().and(hasProgramId(filter.programId()))
				.and(hasRoutineId(filter.routineId())).and(hasScope(filter.scope()))
				.and(hasCurrentStates(filter.currentStates())).and(hasHostId(filter.hostId()))
				.and(hasCreatedBy(filter.createdBy())).and(hasAgentContextId(filter.agentContextId())).and(hasTargetRequestId(filter.targetRequestId()));
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

	private static Specification<HttpJob> hasAgentContextId(Long agentContextId) {
		return (root, query, cb) -> agentContextId == null ? null
				: cb.equal(root.get("agentContext").get("id"), agentContextId);
	}

	// Only narrow jobs point at requests; a wide job has no target request, so it never matches
	private static Specification<HttpJob> hasTargetRequestId(Long targetRequestId) {
		return (root, query, cb) -> {
			if (targetRequestId == null)
				return null;
			Subquery<Long> targets = query.subquery(Long.class);
			Root<NarrowJobTargetRequest> target = targets.from(NarrowJobTargetRequest.class);
			targets.select(target.get("id")).where(cb.equal(target.get("job"), root),
					cb.equal(target.get("targetRequest").get("id"), targetRequestId));
			return cb.exists(targets);
		};
	}

	private static Specification<HttpJob> hasCreatedBy(JobCreator createdBy) {
		return (root, query, cb) -> createdBy == null ? null : cb.equal(root.get("createdBy"), createdBy);
	}

	// A rate of 0 or less does not exist: it is refused wherever it comes from. Null means "not given".
	public static void requireValidRate(Integer rate, String name) {
		if (rate != null && rate < HttpJob.MIN_RATE)
			throw new IllegalArgumentException(name + " must be at least " + HttpJob.MIN_RATE);
	}

	// Null means "not given"
	public static void requireValidPriority(Integer priority, String name) {
		if (priority != null && (priority < HttpJob.MIN_PRIORITY || priority > HttpJob.MAX_PRIORITY))
			throw new IllegalArgumentException(name + " must be between " + HttpJob.MIN_PRIORITY + " and "
					+ HttpJob.MAX_PRIORITY);
	}

}
