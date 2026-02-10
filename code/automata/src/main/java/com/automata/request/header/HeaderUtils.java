package com.automata.request.header;

import com.automata.request.common.enums.ContentType;
import com.automata.response.ResponseContentType;

public abstract class HeaderUtils {

	public static ContentType detectContentType(String contentType) {

		String lower = contentType.toLowerCase().replace(" ", "");

		return switch (lower) {
		case "application/json" -> ContentType.JSON;
		case "application/x-www-form-urlencoded" -> ContentType.FORM;
		// case "multipart/form-data" -> ContentType.MULTIPART;
		default -> ContentType.UNSUPPORTED;
		};

	}

	public static ResponseContentType detectResponseContentType(String mimeType) {

		String lower = mimeType.toLowerCase().replace(" ", "");

		return switch (lower) {
		case "application/json" -> ResponseContentType.JSON;

		default -> ResponseContentType.UNSUPPORTED;
		};
	}

}
