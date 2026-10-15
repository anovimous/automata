package com.automata.request.comparator.common.dto;

import java.util.Set;

import com.automata.request.comparator.common.enums.Schema;

public record ComparatorCreationRequest(String name, Schema schema, Set<Long> modifierIds) {

}
