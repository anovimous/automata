package com.automata.request.equalityset;

import com.automata.request.equalityset.common.dto.RequestEqualitySetResponse;

public abstract class RequestEqualitySetMapper {

	public static RequestEqualitySetResponse toResponse(RequestEqualitySet set) {
		return RequestEqualitySetResponse.builder()
				.id(set.getId())
				.alias(set.getAlias())
				.numberOfRequests(set.getNumberOfRequests())
				.autoApproval(set.isAutoApproval())
				.equalizationId(set.getEqualization().getId())
				.build();
	}

}
