package com.automata.request.comparator.common.dto;

import java.util.Set;

import com.automata.request.comparator.common.enums.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record ComparatorCreationRequest(@NotBlank String name, Schema schema, @NotEmpty Set<Long> modifierIds) {

}
