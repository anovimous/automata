package com.automata.finding.common.exception;

// Thrown when a finding cannot be changed by the caller: it was reviewed by a human, or it was not created by the caller
public class InvalidFindingStateException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidFindingStateException(String message) {
		super(message);
	}

}
