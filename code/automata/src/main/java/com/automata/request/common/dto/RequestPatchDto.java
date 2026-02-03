package com.automata.request.common.dto;

import java.util.Optional;

import com.automata.request.common.enums.ContentType;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.Source;

public record RequestPatchDto(Optional<String> path, Optional<Method> method, Optional<String> extension,
		Optional<Long> tenantId, Optional<Source> source, Optional<String> queryStringBase64,
		Optional<ContentType> contentType, Optional<String> bodyBase64) {

}
