package com.automata.request.common.dto;

import java.time.Instant;

import lombok.Builder;

@Builder
public record EqualizationResponse(Long id, Instant lastRun, boolean autoEqualize, Long comparatorId) {

}
