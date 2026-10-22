package com.automata.request.common.dto;

public record RequestRawResponse(String rawHeadBase64, String rawBodyBase64, boolean bodyStoredAsFile,
		Long responseId) {

}
