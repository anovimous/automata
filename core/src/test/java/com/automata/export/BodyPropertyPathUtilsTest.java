package com.automata.export;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BodyPropertyPathUtilsTest {

	// what the export gave before the brackets and quoted keys were read: it must not change
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "username|username", "user.address.city|user,address,city", "items[0]|items",
			"items[1].name|items,name", "a.b[0][2].c|a,b,c", "matrix[0][1]|matrix", "[0].name|name", "[1].name|name",
			"a b|a b" })
	void thePlainAndIndexedPathsKeepTheirNames(String path, String names) {
		assertEquals(List.of(names.split(",")), BodyPropertyPathUtils.extractNames(path), path);
	}

	@Test
	void aPathWithOnlyAnIndexHasNoName() {
		assertEquals(List.of(), BodyPropertyPathUtils.extractNames("[0]"));
		assertEquals(List.of(), BodyPropertyPathUtils.extractNames(""));
	}

	@Test
	void aFormFieldExportsItsPartsAndItsWholeName() {
		assertEquals(List.of("filter", "name", "filter[name]"), BodyPropertyPathUtils.extractNames("filter[name]"));
		assertEquals(List.of("a", "b", "c", "a[b][c]"), BodyPropertyPathUtils.extractNames("a[b][c]"));
	}

	@Test
	void anEmptyBracketIsAFormFieldWithoutAnInnerName() {
		assertEquals(List.of("tags", "tags[]"), BodyPropertyPathUtils.extractNames("tags[]"));
	}

	@Test
	void aQuotedKeyIsOneName() {
		assertEquals(List.of("a.b"), BodyPropertyPathUtils.extractNames("[\"a.b\"]"));
		assertEquals(List.of("a", "b.c"), BodyPropertyPathUtils.extractNames("a[\"b.c\"]"));
		assertEquals(List.of("a.b", "c"), BodyPropertyPathUtils.extractNames("[\"a.b\"].c"));
		assertEquals(List.of("a.b", "c.d"), BodyPropertyPathUtils.extractNames("[\"a.b\"][\"c.d\"][0]"));
	}

	@Test
	void aQuotedKeyIsNotAFormField() {
		assertEquals(List.of("a[0]"), BodyPropertyPathUtils.extractNames("[\"a[0]\"]"));
		assertEquals(List.of("a", "[x]"), BodyPropertyPathUtils.extractNames("a[\"[x]\"]"));
	}

	@Test
	void quotesInsideAQuotedKeyAreKept() {
		// json-flattener does not escape them
		assertEquals(List.of("a.b\"c"), BodyPropertyPathUtils.extractNames("[\"a.b\"c\"]"));
	}

	@Test
	void aQuoteInAPlainKeyIsPartOfTheName() {
		assertEquals(List.of("x\"y"), BodyPropertyPathUtils.extractNames("x\"y"));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "a[b|a,b", "[\"a.b|a.b", "a[|a", "a.|a", ".a|a" })
	void aMalformedPathNeverThrows(String path, String names) {
		List<String> result = assertDoesNotThrow(() -> BodyPropertyPathUtils.extractNames(path));

		assertEquals(List.of(names.split(",")), result, path);
	}

	@Test
	void onlySeparatorsGiveNoName() {
		assertEquals(List.of(), BodyPropertyPathUtils.extractNames(".."));
	}

	@Test
	void theSameNameIsExportedOnce() {
		assertEquals(List.of("a", "b"), BodyPropertyPathUtils.extractNames("a.b.a.b"));
	}
}
