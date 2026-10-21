package com.automata.finding.common.dto;

import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;

public record FindingCreationRequest(Long requestId, Long jobId, Long vulnerabilityId, double severity,
		FindingStatus status, double confidence, String evidence, String pocReproductionSteps,
		FindingCreator createdBy, String aiDescription) {

}
