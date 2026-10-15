package com.automata.request.comparator;

import com.automata.request.comparator.common.dto.ModifierCreationRequest;
import com.automata.request.comparator.common.dto.ModifierDetailedResponse;
import com.automata.request.comparator.common.dto.ModifierSummaryResponse;

public abstract class ModifierMapper {

	public static ModifierDetailedResponse toDetailedResponse(Modifier modifier) {

		return ModifierDetailedResponse.builder().id(modifier.getId()).creationDate(modifier.getCreationDate())
				.target(modifier.getTarget()).schema(modifier.getSchema()).priority(modifier.getPriority())
				.description(modifier.getDescription()).isAvailable(modifier.isAvailable()).build();

	}

	public static ModifierSummaryResponse toSummaryResponse(Modifier modifier) {

		return ModifierSummaryResponse.builder().id(modifier.getId()).creationDate(modifier.getCreationDate())
				.target(modifier.getTarget()).build();

	}

	public static Modifier toModifier(ModifierCreationRequest req) {
		return Modifier.builder().target(req.target()).schema(req.schema()).priority(req.priority())
				.description(req.description()).isAvailable(req.isAvailable()).build();
	}

}
