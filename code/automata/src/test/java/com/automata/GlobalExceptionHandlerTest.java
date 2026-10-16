package com.automata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.request.common.enums.RequestContentType;

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
	void unsupportedOperationMapsTo501() {
		ResponseEntity<String> response = new GlobalExceptionHandler()
				.handleNotImplemented(new UnsupportedOperationException("later"));

		assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
		assertEquals("later", response.getBody());
	}

}
