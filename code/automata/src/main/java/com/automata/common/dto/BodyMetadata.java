package com.automata.common.dto;

// contentType is the Content-Type header as it was received (null when there was
// none), mimeFamily the category it was classified into
public record BodyMetadata(BodyStorage storage, String contentType, String mimeFamily, long sizeBytes) {

}
