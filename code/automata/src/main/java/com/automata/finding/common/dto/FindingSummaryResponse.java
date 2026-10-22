package com.automata.finding.common.dto;

import java.time.Instant;

import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;

import lombok.Builder;

@Builder
public record FindingSummaryResponse(Long id, Long requestId, Long hostId, Long vulnerabilityId,
		double severity, FindingStatus status, double confidence, FindingCreator createdBy,
		HumanVerdict humanVerdict, Instant creationDate) {

}
