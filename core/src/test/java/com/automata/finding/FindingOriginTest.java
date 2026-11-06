package com.automata.finding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingOrigin;

class FindingOriginTest {

	private static final UUID THIS = UUID.randomUUID();

	@Test
	void aFindingOfTheSameContextIsThisContext() {
		assertEquals(FindingOrigin.THIS_CONTEXT, FindingOrigin.of(FindingCreator.AI, THIS, THIS));
	}

	@Test
	void aFindingOfAnotherContextIsOtherContext() {
		assertEquals(FindingOrigin.OTHER_CONTEXT, FindingOrigin.of(FindingCreator.AI, UUID.randomUUID(), THIS));
	}

	@Test
	void anAiFindingWithoutAContextIsOtherContext() {
		assertEquals(FindingOrigin.OTHER_CONTEXT, FindingOrigin.of(FindingCreator.AI, null, THIS));
	}

	@Test
	void aUserFindingIsUser() {
		assertEquals(FindingOrigin.USER, FindingOrigin.of(FindingCreator.USER, null, THIS));
	}

}
