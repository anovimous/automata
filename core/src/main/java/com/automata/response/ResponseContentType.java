package com.automata.response;

public enum ResponseContentType {
	JSON("application/json"), XML("application/xml"), HTML("text/html"), JS("application/javascript"),
	CSS("text/css"), TEXT("text/plain"), IMAGE("image/*"), AUDIO("audio/*"), VIDEO("video/*"),
	PDF("application/pdf"), ARCHIVE("application/zip"), OCTET_STREAM("application/octet-stream"), OTHER("other");

	private final String rawValue;

	ResponseContentType(String rawValue) {
		this.rawValue = rawValue;
	}

	public String getRaw() {
		return rawValue;
	}
}
