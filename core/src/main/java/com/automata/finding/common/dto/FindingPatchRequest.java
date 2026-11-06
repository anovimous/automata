package com.automata.finding.common.dto;

import java.util.Optional;

import com.automata.finding.common.enums.FindingStatus;

public record FindingPatchRequest(Optional<Double> severity, Optional<FindingStatus> status,
		Optional<Double> confidence, Optional<String> evidence, Optional<String> pocReproductionSteps,
		boolean setPocReproductionStepsNull, Optional<String> aiDescription, boolean setAiDescriptionNull) {

}
