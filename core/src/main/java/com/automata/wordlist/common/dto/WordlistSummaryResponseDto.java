package com.automata.wordlist.common.dto;

import lombok.Builder;

@Builder
public record WordlistSummaryResponseDto(Long id, String name, String description, Integer numberOfLines,
		Long sizeBytes) {

}
