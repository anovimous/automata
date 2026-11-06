package com.automata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.automata.finding.common.exception.InvalidFindingStateException;
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.model.HttpJob;
import com.automata.request.common.enums.RequestContentType;

import jakarta.persistence.OptimisticLockException;

class GlobalExceptionHandlerTest {

	@Test
	void ineligibleJobTargetMapsTo422() {
		IneligibleJobTargetException ex = IneligibleJobTargetException.forRequest(4L, RequestContentType.XML);

		ResponseEntity<String> response = new GlobalExceptionHandler().handleIneligibleJobTarget(ex);

		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
		assertEquals(ex.getMessage(), response.getBody());
	}

	@Test
	void invalidJobStateMapsTo409() {
		InvalidJobStateException ex = new InvalidJobStateException("wrong state");

		ResponseEntity<String> response = new GlobalExceptionHandler().handleInvalidJobState(ex);

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals("wrong state", response.getBody());
	}

	@Test
	void invalidFindingStateMapsTo409() {
		InvalidFindingStateException ex = new InvalidFindingStateException("reviewed");

		ResponseEntity<String> response = new GlobalExceptionHandler().handleInvalidFindingState(ex);

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals("reviewed", response.getBody());
	}

	@Test
	void hostInUseMapsTo409() {
		com.automata.host.common.exception.HostInUseException ex = new com.automata.host.common.exception.HostInUseException(
				"in use");

		ResponseEntity<String> response = new GlobalExceptionHandler().handleHostInUse(ex);

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals("in use", response.getBody());
	}

	@Test
	void wordlistInUseMapsTo409() {
		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleWordlistInUse(new com.automata.wordlist.common.exception.WordlistInUseException("in use"));

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals("in use", response.getBody());
	}

	@Test
	void wordlistAlreadyExistsMapsTo409() {
		ResponseEntity<String> response = new GlobalExceptionHandler().handleWordlistAlreadyExists(
				new com.automata.wordlist.common.exception.WordlistAlreadyExistsException("same file"));

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals("same file", response.getBody());
	}

	@Test
	void uploadOverTheLimitMapsTo413() {
		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleUploadTooLarge(new org.springframework.web.multipart.MaxUploadSizeExceededException(1L));

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
	}

	@Test
	void missingMultipartPartMapsTo400() {
		ResponseEntity<String> response = new GlobalExceptionHandler().handleMissingPart(
				new org.springframework.web.multipart.support.MissingServletRequestPartException("file"));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	void unsupportedMediaTypeMapsTo415() {
		ResponseEntity<String> response = new GlobalExceptionHandler().handleUnsupportedMediaType(
				new org.springframework.web.HttpMediaTypeNotSupportedException("application/json not supported"));

		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
	}

	@Test
	void unreadableRequestBodyMapsTo400() {
		HttpMessageNotReadableException ex = new HttpMessageNotReadableException("JSON parse error", (HttpInputMessage) null);

		ResponseEntity<String> response = new GlobalExceptionHandler().handleUnreadableBody(ex);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(ex.getMessage(), response.getBody());
	}

	@Test
	void unsupportedOperationMapsTo501() {
		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleNotImplemented(new UnsupportedOperationException("later"));

		assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
		assertEquals("later", response.getBody());
	}

	@Test
	void springOptimisticLockFailureMapsTo409() {
		ResponseEntity<String> response = new GlobalExceptionHandler().handleConcurrentModification(
				new ObjectOptimisticLockingFailureException(HttpJob.class, 5L));

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals("The entity was changed at the same time by someone else, please try again",
				response.getBody());
	}

	@Test
	void jpaOptimisticLockFailureMapsTo409() {
		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleConcurrentModification(new OptimisticLockException("stale"));

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
	}

	@Test
	void anUnknownUrlMapsTo404() {
		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleNoResource(new org.springframework.web.servlet.resource.NoResourceFoundException(org.springframework.http.HttpMethod.GET, "api/nothing"));

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertEquals("No resource found for GET /api/nothing", response.getBody());
	}

	@Test
	void aProgramInUseMapsTo409() {
		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleProgramInUse(new com.automata.program.common.exception.ProgramInUseException("busy"));

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals("busy", response.getBody());
	}

	@Test
	void somethingThatAlreadyExistsMapsTo409() {
		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleAlreadyExists(new com.automata.common.exception.AlreadyExistsException("Host 'x' already exists"));

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals("Host 'x' already exists", response.getBody());
	}

	@Test
	void aResponseStatusExceptionWithoutReasonKeepsItsStatus() {
		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleResponseStatus(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN));

		assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
	}

	@Test
	void aMissingParameterMapsTo400() {
		ResponseEntity<String> response = new GlobalExceptionHandler().handleMissingParameter(
				new org.springframework.web.bind.MissingServletRequestParameterException("hostId", "Long"));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	void aDatabaseRefusalWithoutAnySqlStateMapsTo409() {
		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleDataIntegrityViolation(new org.springframework.dao.DataIntegrityViolationException("x"));

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals("The change conflicts with the current data", response.getBody());
	}

	@Test
	void theSqlStateIsFoundDeepInTheCauseChain() {
		Exception deep = new RuntimeException("a", new RuntimeException("b", new java.sql.SQLException("c", "23505")));

		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleDataIntegrityViolation(new org.springframework.dao.DataIntegrityViolationException("x", deep));

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals("A record with the same unique value already exists", response.getBody());
	}

	@Test
	void somethingStillInUseMapsTo409() {
		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleInUse(new com.automata.common.exception.InUseException("still used"));

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals("still used", response.getBody());
	}

	@Test
	void anUnimplementedDeleteMapsTo501() {
		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleNotImplemented(new UnsupportedOperationException("Deleting a program is not implemented yet"));

		assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
		assertEquals("Deleting a program is not implemented yet", response.getBody());
	}
}
