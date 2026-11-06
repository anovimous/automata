package com.automata.response;

import java.util.List;

import com.automata.common.utils.ValidationResult;
import com.automata.request.body.BodyParseResult;
import com.automata.request.header.Header;

import lombok.Builder;

// rawHead / rawBody: the raw response split into its head (status line + headers)
// and its body. bodyStoredAsFile: the body goes to object storage instead of the
// raw table. bodyContentType: the Content-Type header value as received.
@Builder
public record ResponseParseResult(int statusCode, ResponseContentType contentType, int contentLength,
		BodyParseResult bodyParseResult, List<Header> headers, byte[] rawHead, byte[] rawBody,
		boolean bodyStoredAsFile, String bodyContentType, ValidationResult validationResult) {

}
