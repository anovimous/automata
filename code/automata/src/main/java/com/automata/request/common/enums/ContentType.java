package com.automata.request.common.enums;

public enum ContentType {
	JSON("application/json"), MULTIPART("multipart/form-data"), FORM("application/x-www-form-urlencoded"),
	UNSUPPORTED("unsupported");

	private final String rawValue;

	ContentType(String rawValue) {
		this.rawValue = rawValue;
	}

	public String getRaw() {
		return rawValue;
	}
}
