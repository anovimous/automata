package com.automata.request.equalityset;

import com.automata.request.common.dto.EqualizationResponse;

public abstract class EqualizationMapper {

	public static EqualizationResponse toResponse(Equalization e) {

		return EqualizationResponse.builder().id(e.getId()).lastRun(e.getLastRun()).autoEqualize(e.isAutoEqualize())
				.comparatorId(e.getComparator().getId()).build();
	}

}
