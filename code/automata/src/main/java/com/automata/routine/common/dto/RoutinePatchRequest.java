package com.automata.routine.common.dto;

import java.util.Optional;

import com.automata.routine.common.enums.Overhead;
import com.automata.routine.common.enums.PermittedScope;
import com.automata.routine.common.enums.Protocol;

public record RoutinePatchRequest(Optional<String> description, boolean setDescriptionNull,
		Optional<Boolean> isAvailableAtConsumer, Optional<Overhead> overhead, Optional<Protocol> protocol,
		Optional<PermittedScope> permittedScope, Optional<Long> vulnerabilityId, boolean setVulnerabilityNull) {

}
