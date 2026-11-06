package com.automata.response;

public record ResponseFilter(Integer statusCode, ResponseContentType contentType, Integer contentLength, Long hostId) {

}
