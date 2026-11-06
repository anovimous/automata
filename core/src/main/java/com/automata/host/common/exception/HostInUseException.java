package com.automata.host.common.exception;

// Thrown when a host cannot be changed because jobs that act on it are in progress
public class HostInUseException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public HostInUseException(String message) {
		super(message);
	}

}
