package com.automata.wordlist.common.dto;

import lombok.Builder;

// No payloadContentDescription: only GET /api/wordlists/{id} carries it
@Builder
public record WordlistSummaryResponseDto(Long id, String name, String smallDescription, Integer numberOfLines,
		Long sizeBytes) {

}
