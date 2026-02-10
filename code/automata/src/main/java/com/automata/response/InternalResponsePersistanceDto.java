package com.automata.response;

import com.automata.request.body.BodyParseResult;

import lombok.Builder;

@Builder
public record InternalResponsePersistanceDto(int statusCode, ResponseContentType contentType, int contentLength,
		BodyParseResult bodyParseResult) {

}
