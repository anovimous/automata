package com.automata.job.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.request.Request;
import com.automata.request.common.enums.RequestContentType;

class HttpJobUtilsTest {

	private static Request request(Long id, RequestContentType contentType, long bodySizeBytes) {
		Request request = Request.of(id);
		request.setContentType(contentType);
		request.setBodySizeBytes(bodySizeBytes);
		return request;
	}

	@Test
	void jsonAndFormBodiesAreEligible() {
		assertTrue(HttpJobUtils.isJobEligible(RequestContentType.JSON, 10));
		assertTrue(HttpJobUtils.isJobEligible(RequestContentType.FORM, 10));
	}

	@Test
	void bodylessRequestIsEligible() {
		assertTrue(HttpJobUtils.isJobEligible(null, 0));
	}

	@ParameterizedTest
	@EnumSource(value = RequestContentType.class, names = { "JSON", "FORM" }, mode = EnumSource.Mode.EXCLUDE)
	void otherContentTypesWithABodyAreNotEligible(RequestContentType contentType) {
		assertFalse(HttpJobUtils.isJobEligible(contentType, 10));
	}

	@ParameterizedTest
	@EnumSource(RequestContentType.class)
	void anyContentTypeWithoutABodyIsEligible(RequestContentType contentType) {
		assertTrue(HttpJobUtils.isJobEligible(contentType, 0));
	}

	@Test
	void bodyWithoutContentTypeIsNotEligible() {
		assertFalse(HttpJobUtils.isJobEligible(null, 5));
	}

	@Test
	void requestOverloadDelegatesToTheFields() {
		assertTrue(HttpJobUtils.isJobEligible(request(1L, RequestContentType.JSON, 3)));
		assertFalse(HttpJobUtils.isJobEligible(request(2L, RequestContentType.MULTIPART, 3)));
	}

	@Test
	void requireJobEligibleAcceptsEligibleRequest() {
		assertDoesNotThrow(() -> HttpJobUtils.requireJobEligible(request(1L, RequestContentType.FORM, 3)));
	}

	@Test
	void requireJobEligibleRejectsWithDescriptiveMessage() {
		IneligibleJobTargetException ex = assertThrows(IneligibleJobTargetException.class,
				() -> HttpJobUtils.requireJobEligible(request(42L, RequestContentType.MULTIPART, 3)));

		assertTrue(ex.getMessage().contains("Request 42"));
		assertTrue(ex.getMessage().contains("MULTIPART"));
	}

	@Test
	void requireJobEligibleMentionsMissingContentType() {
		IneligibleJobTargetException ex = assertThrows(IneligibleJobTargetException.class,
				() -> HttpJobUtils.requireJobEligible(request(7L, null, 3)));

		assertTrue(ex.getMessage().contains("no Content-Type"));
	}

}
