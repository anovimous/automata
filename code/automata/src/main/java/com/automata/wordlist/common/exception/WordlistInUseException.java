package com.automata.wordlist.common.exception;

// Thrown when a wordlist cannot be deleted because jobs refer to it
public class WordlistInUseException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public WordlistInUseException(String message) {
		super(message);
	}

}
