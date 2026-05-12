package com.automata.request.header;

import com.automata.request.common.enums.RequestContentType;
import com.automata.response.ResponseContentType;

public abstract class HeaderUtils {

	public static RequestContentType detectRequestContentType(String contentType) {

		String lower = contentType.toLowerCase().replace(" ", "");

		return switch (lower) {
		case "application/json" -> RequestContentType.JSON;
		case "application/x-www-form-urlencoded" -> RequestContentType.FORM;
		// case "multipart/form-data" -> ContentType.MULTIPART;
		default -> RequestContentType.UNSUPPORTED;
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
