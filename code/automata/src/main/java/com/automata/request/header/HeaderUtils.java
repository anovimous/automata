package com.automata.request.header;

import com.automata.request.common.enums.ContentType;

public abstract class HeaderUtils {

	public static ContentType detectContentType(String contentType) {

		String lower = contentType.toLowerCase().replace(" ", "");

		return switch (lower) {
		case "application/json" -> ContentType.JSON;
		case "application/x-www-form-urlencoded" -> ContentType.JSON;
		// case "multipart/form-data" -> ContentType.MULTIPART;
		default -> ContentType.UNSUPPORTED;
		};

	}

}
