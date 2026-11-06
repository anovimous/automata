package com.automata.program.common.exception;

// Thrown when a program cannot be changed because jobs of it are in progress
public class ProgramInUseException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ProgramInUseException(String message) {
		super(message);
	}

}
