package com.automata.finding.common.dto;

import com.automata.finding.common.enums.HumanVerdict;

import jakarta.validation.constraints.NotNull;

public record FindingReviewRequest(@NotNull HumanVerdict verdict, String comment) {

}
