package com.automata.request.parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.automata.request.common.dto.QueryParameterInternalDto;
import com.automata.testsupport.KnownBug;

class QueryParameterUtilsTest {

	private static List<String> pairs(QueryStringParseResult result) {
		return result.getQueryParameters().stream().map(p -> p.getParameter() + "=" + p.getValue()).toList();
	}

	@Test
	void parsesNameValuePairsInOrder() {
		assertEquals(List.of("a=1", "b=2"), pairs(QueryParameterUtils.parseQueryString("a=1&b=2")));
	}

	@Test
	void aLeadingQuestionMarkIsIgnored() {
		assertEquals(List.of("a=1"), pairs(QueryParameterUtils.parseQueryString("?a=1")));
	}

	@Test
	void emptyQueryStringHasNoParameters() {
		assertTrue(QueryParameterUtils.parseQueryString("").getQueryParameters().isEmpty());
	}

	@Test
	void percentEncodingAndPlusAreDecoded() {
		assertEquals(List.of("q=a b", "r=x y&z"), pairs(QueryParameterUtils.parseQueryString("q=a%20b&r=x+y%26z")));
	}

	@Test
	void aRepeatedParameterKeepsEveryOccurrence() {
		assertEquals(List.of("id=1", "id=2"), pairs(QueryParameterUtils.parseQueryString("id=1&id=2")));
	}

	@Test
	void anEmptyValueIsAnEmptyString() {
		assertEquals(List.of("a="), pairs(QueryParameterUtils.parseQueryString("a=")));
	}

	@Test
	@KnownBug("BUG-021")
	void aParameterWithoutValueIsStoredWithAnEmptyValue() {
		// the column query_parameter.value is NOT NULL, a null value cannot be saved
		QueryStringParseResult result = QueryParameterUtils.parseQueryString("debug&a=1");

		assertNotNull(result.getQueryParameters().get(0).getValue());
	}

	@Test
	void composeOfNoParametersIsEmpty() {
		assertEquals(Optional.empty(), QueryParameterUtils.composeRawQueryString(List.of()));
	}

	@Test
	void composeEncodesTheValues() {
		Optional<String> query = QueryParameterUtils.composeRawQueryString(List.of(
				new QueryParameterInternalDto("q", "a b", 1L), new QueryParameterInternalDto("r", "x&y=z", 1L)));

		assertEquals("q=a+b&r=x%26y%3Dz", query.orElseThrow());
	}

	@Test
	void parseThenComposeRoundTrips() {
		QueryStringParseResult parsed = QueryParameterUtils.parseQueryString("a=1&b=hello+world&c=%26");
		List<QueryParameterInternalDto> dtos = parsed.getQueryParameters().stream()
				.map(p -> new QueryParameterInternalDto(p.getParameter(), p.getValue(), 1L)).toList();

		assertEquals("a=1&b=hello+world&c=%26", QueryParameterUtils.composeRawQueryString(dtos).orElseThrow());
	}
}
