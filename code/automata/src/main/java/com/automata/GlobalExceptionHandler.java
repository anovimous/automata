package com.automata;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.exception.InvalidJobStateException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<String> handleNotFound(EntityNotFoundException ex) {
		log.info("\n\n\n\nNot found: {}\n\n\n\n", ex.getMessage());
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

	@ExceptionHandler({ ObjectOptimisticLockingFailureException.class, OptimisticLockException.class })
	public ResponseEntity<String> handleConcurrentModification(Exception ex) {
		log.info("Concurrent modification: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body("The entity was changed at the same time by someone else, please try again");
	}

	@ExceptionHandler(UnsupportedOperationException.class)
	public ResponseEntity<String> handleNotImplemented(UnsupportedOperationException ex) {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(ex.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
		log.warn("\n\n\n\nBad request: {}\n\n\n\n", ex.getMessage(), ex);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleGeneric(Exception ex) {
		log.error("\n\n\n\nUnhandled exception: {}\n\n\n\n", ex.getMessage(), ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
	}
}
