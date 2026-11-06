package com.automata.response.common.exception;

// Thrown when a response is added to a request which already has one (a request has at most one response)
public class ResponseAlreadyExistsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ResponseAlreadyExistsException(Long requestId, Long responseId) {
		super(String.format("Request %d already has a response (id %d), so another one can't be added.", requestId,
				responseId));
	}

}
