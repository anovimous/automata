package com.automata.response;

public record RawResponseDto(String rawHeadBase64, String rawBodyBase64, boolean bodyStoredAsFile) {

}
