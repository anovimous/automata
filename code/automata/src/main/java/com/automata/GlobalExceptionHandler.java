package com.automata;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.exception.InvalidJobStateException;

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

	@ExceptionHandler(IneligibleJobTargetException.class)
	public ResponseEntity<String> handleIneligibleJobTarget(IneligibleJobTargetException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
	}

	@ExceptionHandler(InvalidJobStateException.class)
	public ResponseEntity<String> handleInvalidJobState(InvalidJobStateException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
	}

	@ExceptionHandler(UnsupportedOperationException.class)
	public ResponseEntity<String> handleNotImplemented(UnsupportedOperationException ex) {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(ex.getMessage());
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
