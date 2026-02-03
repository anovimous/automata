package com.automata.request;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.automata.common.utils.ValidationResult;
import com.automata.host.common.dto.RequestFilter;

import jakarta.persistence.criteria.Predicate;

public abstract class RequestUtils {

	public static ValidationResult validateRequestFilter(RequestFilter filter) {
		// validate that either hostId or programId is always present
		if (filter.programId() != null || filter.hostId() != null)
			return ValidationResult.valid();
		else
			return ValidationResult.invalid();

	}

	public static Specification<Request> buildSpecification(RequestFilter filter) {

		return (root, query, cb) -> {

			List<Predicate> predicates = new ArrayList<>();

			if (filter.programId() != null) {
				predicates.add(cb.equal(root.join("program").get("id"), filter.programId()));
			}

			if (filter.hostId() != null) {
				predicates.add(cb.equal(root.join("host").get("id"), filter.hostId()));
			}

			if (filter.tenantId() != null) {
				predicates.add(cb.equal(root.join("tenant").get("id"), filter.tenantId()));
			}

			if (filter.source() != null) {
				predicates.add(cb.equal(root.get("source"), filter.source()));
			}

			if (filter.method() != null) {
				predicates.add(cb.equal(root.get("method"), filter.method()));
			}

			if (filter.computatedPath() != null && !filter.computatedPath().isBlank()) {
				predicates.add(cb.like(cb.lower(root.get("computatedPath")),
						"%" + filter.computatedPath().toLowerCase() + "%"));
			}

			if (filter.extension() != null && !filter.extension().isBlank()) {
				predicates.add(cb.equal(root.get("extension"), filter.extension()));
			}

			if (filter.contentType() != null) {
				predicates.add(cb.equal(root.get("contentType"), filter.contentType()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));

		};

	}

}
