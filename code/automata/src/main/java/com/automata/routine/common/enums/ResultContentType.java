package com.automata.routine.common.enums;

import org.springframework.http.MediaType;

public enum ResultContentType {

	JSON(MediaType.APPLICATION_JSON), TEXT(MediaType.TEXT_PLAIN);

	private final MediaType mediaType;

	ResultContentType(MediaType mediaType) {
		this.mediaType = mediaType;
	}

	public MediaType mediaType() {
		return mediaType;
	}

}
