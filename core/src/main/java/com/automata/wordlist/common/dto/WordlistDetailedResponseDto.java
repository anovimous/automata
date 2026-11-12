package com.automata.wordlist.common.dto;

import lombok.Builder;

@Builder
public record WordlistDetailedResponseDto(Long id, String name, String smallDescription,
		String payloadContentDescription, Integer numberOfLines, Long sizeBytes, String sha256) {

}
