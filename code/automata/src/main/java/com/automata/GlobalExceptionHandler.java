package com.automata;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.persistence.EntityNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<String> handleNotFound(EntityNotFoundException ex) {
		System.out.print("\n\n\n\n");
		System.out.println(ex.getMessage());
		System.out.print("\n\n\n\n");
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
		System.out.print("\n\n\n\n");
		System.out.println(ex.getMessage());
		ex.printStackTrace();
		System.out.print("\n\n\n\n");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleGeneric(Exception ex) {
		System.out.print("\n\n\n\n");
		System.out.println(ex.getMessage());
		ex.printStackTrace();
		System.out.print("\n\n\n\n");
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
	}
}
