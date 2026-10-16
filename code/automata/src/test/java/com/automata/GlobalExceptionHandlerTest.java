package com.automata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.request.common.enums.RequestContentType;

class GlobalExceptionHandlerTest {

	@Test
	void ineligibleJobTargetMapsTo422() {
		IneligibleJobTargetException ex = IneligibleJobTargetException.forRequest(4L, RequestContentType.XML);

		ResponseEntity<String> response = new GlobalExceptionHandler().handleIneligibleJobTarget(ex);

		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
		assertEquals(ex.getMessage(), response.getBody());
	}

}
