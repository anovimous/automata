package com.automata.request.common.dto;

import com.automata.request.common.enums.RequestContentType;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.Source;

import lombok.Builder;

@Builder
public record RequestResponse(Method method, String computatedPath, String extension, String version,
		int numberOfProperties, RequestContentType contentType, Source source, Long hostId, Long tenantId,
		Long programId, Long responseId) {

}
