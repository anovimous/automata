package com.automata.request.comparator.common.dto;

import com.automata.request.comparator.common.enums.Schema;
import com.automata.request.comparator.hash.HashTarget;

public record ModifierCreationRequest(HashTarget target, String description, Long priority, Schema schema,
		Boolean isAvailable) {

}
