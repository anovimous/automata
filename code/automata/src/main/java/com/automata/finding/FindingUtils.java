package com.automata.finding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.automata.finding.common.dto.FindingFilter;
import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;
import com.automata.request.equalityset.RequestEqualityRelation;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public abstract class FindingUtils {

	public static Specification<Finding> buildSpecification(FindingFilter filter) {
		Specification<Finding> spec = Specification.where(null);
		if (filter.hostId() != null)
			spec = spec.and(hasHostId(filter.hostId()));
		if (filter.requestId() != null)
			spec = spec.and(hasRequestId(filter.requestId()));
		if (filter.jobId() != null)
			spec = spec.and(hasJobId(filter.jobId()));
		if (filter.status() != null)
			spec = spec.and(hasStatus(filter.status()));
		if (filter.humanVerdict() != null)
			spec = spec.and(hasHumanVerdict(filter.humanVerdict()));
		if (filter.createdBy() != null)
			spec = spec.and(hasCreatedBy(filter.createdBy()));
		return spec;
	}

	// The findings of the given requests and of every request of the given equality sets
	public static Specification<Finding> inRequestsOrEqualitySets(Collection<Long> requestIds,
			Collection<Long> equalitySetIds) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (!requestIds.isEmpty())
				predicates.add(root.get("request").get("id").in(requestIds));
			if (!equalitySetIds.isEmpty()) {
				Subquery<Long> members = query.subquery(Long.class);
				Root<RequestEqualityRelation> relation = members.from(RequestEqualityRelation.class);
				members.select(relation.get("request").get("id")).where(relation.get("set").get("id").in(equalitySetIds));
				predicates.add(root.get("request").get("id").in(members));
			}
			return predicates.isEmpty() ? cb.disjunction() : cb.or(predicates.toArray(new Predicate[0]));
		};
	}

	private static Specification<Finding> hasHostId(Long hostId) {
		return (root, query, cb) -> cb.equal(root.get("host").get("id"), hostId);
	}

	private static Specification<Finding> hasRequestId(Long requestId) {
		return (root, query, cb) -> cb.equal(root.get("request").get("id"), requestId);
	}

	private static Specification<Finding> hasJobId(Long jobId) {
		return (root, query, cb) -> cb.equal(root.get("job").get("id"), jobId);
	}

	private static Specification<Finding> hasStatus(FindingStatus status) {
		return (root, query, cb) -> cb.equal(root.get("status"), status);
	}

	private static Specification<Finding> hasHumanVerdict(HumanVerdict humanVerdict) {
		return (root, query, cb) -> cb.equal(root.get("humanVerdict"), humanVerdict);
	}

	private static Specification<Finding> hasCreatedBy(FindingCreator createdBy) {
		return (root, query, cb) -> cb.equal(root.get("createdBy"), createdBy);
	}

}
