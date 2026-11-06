package com.automata.request.common.dto;

import java.time.LocalDate;
import java.util.List;

import com.automata.common.dto.BodyMetadata;
import com.automata.common.dto.HeaderEntry;
import com.automata.request.common.enums.Source;

import lombok.Builder;

@Builder
public record RequestMetadataResponse(Long requestId, Long hostId, Long programId, Long tenantId, Long responseId,
		Source source, LocalDate insertionDate, RequestLine line, List<HeaderEntry> headers, BodyMetadata body) {

}
