package com.automata.request.comparator.common.dto;

import java.time.LocalDate;

import com.automata.request.comparator.common.enums.Schema;
import com.automata.request.comparator.hash.HashTarget;

import lombok.Builder;

@Builder
public record ModifierDetailedResponse(Long id, LocalDate creationDate, HashTarget target, String description,
		Long priority, Schema schema, Boolean isAvailable) {

}
