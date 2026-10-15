package com.automata.request.comparator;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.automata.request.comparator.common.enums.Schema;

import jakarta.persistence.criteria.Predicate;

public abstract class ComparatorUtils {

	public static Specification<Comparator> buildSpecification(String name, Schema schema) {
		return (root, query, cb) -> {

			List<Predicate> predicates = new ArrayList<>();

			if (name != null && !name.isBlank()) {
				predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
			}

			if (schema != null) {
				predicates.add(cb.equal(root.get("schema"), schema));
			}

			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}
