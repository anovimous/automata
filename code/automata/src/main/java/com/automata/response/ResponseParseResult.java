package com.automata.response;

import java.util.List;

import com.automata.common.utils.ValidationResult;
import com.automata.request.body.BodyParseResult;
import com.automata.request.header.Header;

import lombok.Builder;

@Builder
public record ResponseParseResult(int statusCode, ResponseContentType contentType, int contentLength,
		BodyParseResult bodyParseResult, List<Header> headers, ValidationResult validationResult) {

}
