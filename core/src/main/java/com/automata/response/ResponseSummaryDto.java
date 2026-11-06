package com.automata.response;

import java.time.LocalDate;

import lombok.Builder;

@Builder
public record ResponseSummaryDto(Long responseId, Long requestId, Long hostId, Integer statusCode,
		ResponseContentType contentType, Integer contentLength, LocalDate insertionDate, boolean bodyStoredAsFile,
		long bodySizeBytes) {
}
