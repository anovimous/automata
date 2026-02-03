package com.automata.host.common.dto;

import com.automata.request.common.enums.ContentType;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.Source;

public record RequestFilter(Long hostId, Long programId, Long tenantId, Source source, Method method,
		String computatedPath, String extension, ContentType contentType) {

}
