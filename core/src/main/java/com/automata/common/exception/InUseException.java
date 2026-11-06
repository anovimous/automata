package com.automata.common.exception;

// Thrown when something cannot be deleted because other records still refer to it
public class InUseException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InUseException(String message) {
		super(message);
	}

}
