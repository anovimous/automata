package com.automata.agentcontext.common.dto;

import java.time.LocalDate;

import com.automata.request.common.enums.Method;

import lombok.Builder;

// A request the agent can read. resolvedTarget tells whether it is also one of the
// context's resolved targets (the only requests a job can target).
@Builder
public record ContextRequestSummary(Long requestId, Method method, String computatedPath, Long equalitySetId,
		Long tenantId, boolean resolvedTarget, boolean automataJobEligible, LocalDate insertionDate) {

}
