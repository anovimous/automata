package com.automata.finding.common.dto;

import java.time.Instant;

import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;

import lombok.Builder;

@Builder
public record FindingDetailedResponse(Long id, Instant creationDate, Long requestId, Long hostId, Long jobId,
		Long vulnerabilityId, double severity, FindingStatus status, double confidence, String evidence,
		String pocReproductionSteps, FindingCreator createdBy, String aiDescription, HumanVerdict humanVerdict,
		String humanComment, Instant reviewedAt) {

}
