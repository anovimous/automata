package com.automata.request.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.enums.PropertyValueType;
import com.automata.request.common.enums.RequestContentType;

class BodyUtilsComposeTest {

	@Test
	void formBodyIsComposed() {
		List<BodyPropertyInternalDto> dtos = List.of(
				new BodyPropertyInternalDto("a", "1", PropertyValueType.STRING, 1L, null),
				new BodyPropertyInternalDto("b", "x y", PropertyValueType.STRING, 1L, null));

		assertEquals("a=1&b=x+y", BodyUtils.composeRequestRawBody(RequestContentType.FORM, dtos).orElseThrow());
	}

	@Test
	void jsonBodyIsComposed() {
		List<BodyPropertyInternalDto> dtos = List
				.of(new BodyPropertyInternalDto("a", "1", PropertyValueType.STRING, 1L, null));

		assertEquals("{\"a\":\"1\"}", BodyUtils.composeRequestRawBody(RequestContentType.JSON, dtos).orElseThrow());
	}

	@ParameterizedTest
	@EnumSource(value = RequestContentType.class, names = { "JSON", "FORM" }, mode = EnumSource.Mode.EXCLUDE)
	void otherContentTypesAreRejected(RequestContentType contentType) {
		assertThrows(IllegalArgumentException.class, () -> BodyUtils.composeRequestRawBody(contentType, List.of()));
	}

}
