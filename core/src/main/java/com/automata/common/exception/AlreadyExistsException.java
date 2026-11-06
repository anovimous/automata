package com.automata.common.exception;

// Thrown when something is created with a value that has to be unique and already exists (a host name, a routine key)
public class AlreadyExistsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AlreadyExistsException(String message) {
		super(message);
	}

}
