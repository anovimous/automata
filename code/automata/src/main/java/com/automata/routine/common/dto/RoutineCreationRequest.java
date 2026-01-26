package com.automata.routine.common.dto;

import com.automata.routine.common.enums.Overhead;
import com.automata.routine.common.enums.PermittedScope;
import com.automata.routine.common.enums.Protocol;

public record RoutineCreationRequest(String name, String description, Boolean isAvailableAtConsumer, Overhead overhead,
		Protocol protocol, PermittedScope permittedScope, Long vulnerabilityId) {

}
