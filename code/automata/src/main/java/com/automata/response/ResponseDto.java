package com.automata.response;

import java.time.LocalDate;

import lombok.Builder;

@Builder
public record ResponseDto(Integer statusCode, ResponseContentType contentType, Integer contentLength,
		LocalDate insertionDate, Long requestId) {
}
