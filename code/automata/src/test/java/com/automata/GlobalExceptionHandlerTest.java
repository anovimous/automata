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

}
