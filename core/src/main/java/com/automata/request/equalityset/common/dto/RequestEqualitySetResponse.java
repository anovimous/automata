package com.automata.request.equalityset.common.dto;

import lombok.Builder;

@Builder
public record RequestEqualitySetResponse(Long id, String alias, int numberOfRequests, boolean autoApproval,
		Long equalizationId) {

}
