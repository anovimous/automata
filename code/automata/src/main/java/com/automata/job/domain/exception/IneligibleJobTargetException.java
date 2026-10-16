package com.automata.job.domain.exception;

import com.automata.request.common.enums.RequestContentType;

// Thrown when a request can't be used as a job target because the job domain can't rebuild it faithfully
public class IneligibleJobTargetException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public IneligibleJobTargetException(String message) {
		super(message);
	}

	public static IneligibleJobTargetException forRequest(Long requestId, RequestContentType contentType) {

		String bodyDescription = contentType == null ? "its body has no Content-Type header"
				: "its body has content type " + contentType.name();

		return new IneligibleJobTargetException(String.format(
				"Request %d can't be used as a job target: %s, and jobs only support requests with a JSON or FORM body, or with no body.",
				requestId, bodyDescription));
	}

}
