package com.automata.response;

public enum ResponseContentType {
	JSON("application/json"), JS("application/javascript"), UNSUPPORTED("unsupported");

	private final String rawValue;

	ResponseContentType(String rawValue) {
		this.rawValue = rawValue;
	}

	public String getRaw() {
		return rawValue;
	}
}
