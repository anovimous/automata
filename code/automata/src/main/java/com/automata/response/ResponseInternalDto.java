package com.automata.response;

import lombok.Builder;

@Builder
public record ResponseInternalDto(Long id, Integer statusCode, ResponseContentType contentType, Integer contentLength) {

}
