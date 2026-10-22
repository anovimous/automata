package com.automata.finding.common.dto;

import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;

public record FindingFilter(Long hostId, Long requestId, Long jobId, FindingStatus status,
		HumanVerdict humanVerdict, FindingCreator createdBy) {

}
