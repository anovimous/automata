package com.automata;

import java.sql.SQLException;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.automata.common.exception.AlreadyExistsException;
import com.automata.finding.common.exception.InvalidFindingStateException;
import com.automata.host.common.exception.HostInUseException;
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.exception.JobNotApprovedException;
import com.automata.program.common.exception.ProgramInUseException;
import com.automata.response.common.exception.ResponseAlreadyExistsException;
import com.automata.wordlist.common.exception.WordlistAlreadyExistsException;
import com.automata.wordlist.common.exception.WordlistInUseException;

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

	@ExceptionHandler(HostInUseException.class)
	public ResponseEntity<String> handleHostInUse(HostInUseException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
	}

	@ExceptionHandler(WordlistInUseException.class)
	public ResponseEntity<String> handleWordlistInUse(WordlistInUseException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
	}

	@ExceptionHandler(ProgramInUseException.class)
	public ResponseEntity<String> handleProgramInUse(ProgramInUseException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
	}

	@ExceptionHandler(AlreadyExistsException.class)
	public ResponseEntity<String> handleAlreadyExists(AlreadyExistsException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
	}

	// The errors that Spring MVC raises itself. The catch-all handler below would turn them
	// into a 500, so each one is answered like Spring answers it: the fault is the caller's.

	// an id that is not a number, a value that is not an enum constant, ...
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<String> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(String.format("The parameter '%s' has an invalid value '%s'",
				ex.getName(), shortened(String.valueOf(ex.getValue()))));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<String> handleMissingParameter(MissingServletRequestParameterException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
	}

	// a filter (@ModelAttribute) that cannot be bound, for instance an unknown state in a query
	@ExceptionHandler(BindException.class)
	public ResponseEntity<String> handleBindingFailure(BindException ex) {
		String fields = ex.getFieldErrors().stream().map(error -> "'" + error.getField() + "'").distinct()
				.collect(Collectors.joining(", "));
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(fields.isEmpty() ? "The request has an invalid value" : "The request has an invalid value for " + fields);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<String> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
		ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
		if (ex.getSupportedHttpMethods() != null)
			response.allow(ex.getSupportedHttpMethods().toArray(new HttpMethod[0]));
		return response.body(ex.getMessage());
	}

	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	public ResponseEntity<String> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
		return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(ex.getMessage());
	}

	// an unknown URL
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<String> handleNoResource(NoResourceFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No resource found for " + ex.getHttpMethod() + " /" + ex.getResourcePath());
	}

	// an exception which carries its own status
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<String> handleResponseStatus(ResponseStatusException ex) {
		return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason() != null ? ex.getReason() : ex.getMessage());
	}

	// The database refused a change. The constraint is told apart by the SQL state, and the message
	// says what is wrong without repeating the SQL or the names of the tables.
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<String> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		log.warn("Database constraint violation: {}", ex.getMostSpecificCause().getMessage());
		String state = sqlStateOf(ex);
		return switch (state == null ? "" : state) {
		case "23505" -> ResponseEntity.status(HttpStatus.CONFLICT).body("A record with the same unique value already exists");
		case "23503" -> ResponseEntity.status(HttpStatus.CONFLICT)
				.body("The change conflicts with related records: the record is still used by other records, or refers to one that does not exist");
		case "23502" -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body("A required value is missing");
		case "23514" -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body("A value is not allowed");
		default -> state != null && state.startsWith("22")
				? ResponseEntity.status(HttpStatus.BAD_REQUEST).body("A value is invalid or too long")
				: ResponseEntity.status(HttpStatus.CONFLICT).body("The change conflicts with the current data");
		};
	}

	// the SQL state of the exception of the database at the bottom of the chain
	private static String sqlStateOf(Throwable throwable) {
		for (Throwable cause = throwable; cause != null; cause = cause.getCause())
			if (cause instanceof SQLException sql && sql.getSQLState() != null)
				return sql.getSQLState();
		return null;
	}

	private static String shortened(String text) {
		return text.length() <= 60 ? text : text.substring(0, 60) + "...";
	}

	@ExceptionHandler(ResponseAlreadyExistsException.class)
	public ResponseEntity<String> handleResponseAlreadyExists(ResponseAlreadyExistsException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
	}

	@ExceptionHandler(WordlistAlreadyExistsException.class)
	public ResponseEntity<String> handleWordlistAlreadyExists(WordlistAlreadyExistsException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
	}

	// an upload over the limit (spring.servlet.multipart.max-file-size)
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<String> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body("The uploaded file is too large");
	}

	// a multipart request without a part it needs
	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<String> handleMissingPart(MissingServletRequestPartException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<String> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ex.getMessage());
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
