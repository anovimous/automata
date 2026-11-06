package com.automata.wordlist.common.exception;

// Thrown when the file of a new wordlist is the same as the file of an existing one
public class WordlistAlreadyExistsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public WordlistAlreadyExistsException(String message) {
		super(message);
	}

}
