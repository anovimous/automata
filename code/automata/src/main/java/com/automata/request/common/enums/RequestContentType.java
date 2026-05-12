package com.automata.request.common.enums;

public enum RequestContentType {
	JSON("application/json"), MULTIPART("multipart/form-data"), FORM("application/x-www-form-urlencoded"),
	UNSUPPORTED("unsupported");

	private final String rawValue;

	RequestContentType(String rawValue) {
		this.rawValue = rawValue;
	}

	public String getRaw() {
		return rawValue;
	}
}
