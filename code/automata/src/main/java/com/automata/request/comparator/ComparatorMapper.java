package com.automata.request.comparator;

import com.automata.request.comparator.common.dto.ComparatorCreationRequest;
import com.automata.request.comparator.common.dto.ComparatorResponse;

public abstract class ComparatorMapper {

	public static ComparatorResponse toResponse(Comparator comparator) {

		return ComparatorResponse.builder().id(comparator.getId()).creationDate(comparator.getCreationDate())
				.name(comparator.getName()).schema(comparator.getSchema()).build();

	}

	public static Comparator toComparator(ComparatorCreationRequest req) {

		return Comparator.builder().name(req.name()).schema(req.schema()).build();

	}

}
