package com.automata.request.comparator.common.dto;

import java.time.LocalDate;

import com.automata.request.comparator.common.enums.Schema;

import lombok.Builder;

@Builder
public record ComparatorResponse(Long id, LocalDate creationDate, String name, Schema schema) {

}
