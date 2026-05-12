package com.automata.request.comparator.common.dto;

import java.util.Optional;

import com.automata.request.comparator.common.enums.Schema;

public record ComparatorPatchRequest(Optional<String> name, Optional<Schema> schema, boolean setSchemaNull,
		Optional<Long> programId, Optional<Long> hostId) {

}
