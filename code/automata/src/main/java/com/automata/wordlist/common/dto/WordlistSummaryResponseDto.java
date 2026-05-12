package com.automata.wordlist.common.dto;

import lombok.Builder;

@Builder
public record WordlistSummaryResponseDto(Long id, String name, Integer numberOfLines) {

}
