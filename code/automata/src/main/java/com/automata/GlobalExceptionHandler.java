package com.automata;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.automata.finding.common.exception.InvalidFindingStateException;
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.exception.JobNotApprovedException;

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

	@ExceptionHandler(InvalidFindingStateException.class)
	public ResponseEntity<String> handleInvalidFindingState(InvalidFindingStateException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
	}

	@ExceptionHandler(JobNotApprovedException.class)
	public ResponseEntity<String> handleJobNotApproved(JobNotApprovedException ex) {
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

	// a body which is not JSON, or has a value the type does not have (an unknown
	// enum name, for instance), is the fault of the caller
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<String> handleUnreadableBody(HttpMessageNotReadableException ex) {
		log.info("Unreadable request body: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
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
