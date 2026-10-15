package com.automata.common.dto;

// A stored raw request or response, split into head and body (both base64).
// rawBodyBase64 is null when there is no body, or when it is stored as a file
public record RawHttpMessage(String rawHeadBase64, String rawBodyBase64, boolean bodyStoredAsFile) {

}
