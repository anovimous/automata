package com.automata.response;

import java.time.LocalDate;
import java.util.List;

import com.automata.common.dto.BodyMetadata;
import com.automata.common.dto.HeaderEntry;

import lombok.Builder;

@Builder
public record ResponseMetadataDto(Long responseId, Long requestId, Long hostId, Integer statusCode,
		LocalDate insertionDate, List<HeaderEntry> headers, BodyMetadata body) {
}
