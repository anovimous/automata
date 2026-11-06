package com.automata.finding.common.dto;

import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FindingCreationRequest(@NotNull Long requestId, Long jobId, @NotNull Long vulnerabilityId, double severity,
		@NotNull FindingStatus status, double confidence, @NotBlank String evidence, String pocReproductionSteps,
		@NotNull FindingCreator createdBy, String aiDescription) {

}
