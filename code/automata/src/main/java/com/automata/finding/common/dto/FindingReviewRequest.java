package com.automata.finding.common.dto;

import com.automata.finding.common.enums.HumanVerdict;

public record FindingReviewRequest(HumanVerdict verdict, String comment) {

}
