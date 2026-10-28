package com.automata.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class PhrasesTest {

	@Test
	void oneIsSingular() {
		assertEquals("1 finding", Phrases.count(1, "finding", "findings"));
	}

	@Test
	void zeroAndManyArePlural() {
		assertEquals("0 findings", Phrases.count(0, "finding", "findings"));
		assertEquals("2 findings", Phrases.count(2, "finding", "findings"));
		assertEquals("1000 findings", Phrases.count(1000, "finding", "findings"));
	}

	@Test
	void aListOfNoPartsIsEmpty() {
		assertEquals("", Phrases.list(List.of()));
	}

	@Test
	void aListOfOnePartIsThatPart() {
		assertEquals("1 job", Phrases.list(List.of("1 job")));
	}

	@Test
	void aListOfTwoPartsUsesAnd() {
		assertEquals("1 job and 2 findings", Phrases.list(List.of("1 job", "2 findings")));
	}

	@Test
	void aListOfManyPartsUsesCommasAndTheLastAnd() {
		assertEquals("1 job, 2 findings and 3 programs", Phrases.list(List.of("1 job", "2 findings", "3 programs")));
	}

}
