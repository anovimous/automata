package com.automata.request.equalityset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.automata.request.equalityset.common.dto.RequestEqualitySetResponse;

class RequestEqualitySetMapperTest {

	@Test
	void toResponseMapsAllFields() {
		Equalization equalization = Equalization.builder().id(10L).build();
		RequestEqualitySet set = RequestEqualitySet.builder()
				.id(1L)
				.alias("GET /api/users")
				.numberOfRequests(5)
				.equalization(equalization)
				.autoApproval(true)
				.build();

		RequestEqualitySetResponse response = RequestEqualitySetMapper.toResponse(set);

		assertEquals(1L, response.id());
		assertEquals("GET /api/users", response.alias());
		assertEquals(5, response.numberOfRequests());
		assertTrue(response.autoApproval());
		assertEquals(10L, response.equalizationId());
	}

	@Test
	void toResponseMapsAutoApprovalFalse() {
		Equalization equalization = Equalization.builder().id(10L).build();
		RequestEqualitySet set = RequestEqualitySet.builder()
				.id(2L)
				.alias("POST /api/orders")
				.numberOfRequests(3)
				.equalization(equalization)
				.build();

		RequestEqualitySetResponse response = RequestEqualitySetMapper.toResponse(set);

		assertFalse(response.autoApproval());
	}

}
