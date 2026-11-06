package com.automata.request.comparator.common.dto;

import com.automata.request.comparator.common.enums.Schema;
import com.automata.request.comparator.hash.HashTarget;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ModifierCreationRequest(@NotNull HashTarget target, @NotBlank String description, @NotNull Long priority, Schema schema,
		@NotNull Boolean isAvailable) {

}
