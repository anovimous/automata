package com.automata.request.common.enums;

public enum RequestContentType {
	JSON("application/json"), FORM("application/x-www-form-urlencoded"), MULTIPART("multipart/form-data"),
	XML("application/xml"), HTML("text/html"), JS("application/javascript"), CSS("text/css"), TEXT("text/plain"),
	IMAGE("image/*"), AUDIO("audio/*"), VIDEO("video/*"), PDF("application/pdf"), ARCHIVE("application/zip"),
	OCTET_STREAM("application/octet-stream"), OTHER("other");

	private final String rawValue;

	RequestContentType(String rawValue) {
		this.rawValue = rawValue;
	}

	public String getRaw() {
		return rawValue;
	}
}
