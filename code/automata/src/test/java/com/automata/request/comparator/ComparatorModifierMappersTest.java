package com.automata.request.comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.automata.request.comparator.common.dto.ComparatorCreationRequest;
import com.automata.request.comparator.common.dto.ComparatorResponse;
import com.automata.request.comparator.common.dto.ModifierCreationRequest;
import com.automata.request.comparator.common.dto.ModifierDetailedResponse;
import com.automata.request.comparator.common.dto.ModifierSummaryResponse;
import com.automata.request.comparator.common.enums.Schema;
import com.automata.request.comparator.hash.HashTarget;
import com.automata.testsupport.KnownBug;

class ComparatorModifierMappersTest {

	@Test
	void comparatorResponseCarriesTheScalarFields() {
		Comparator comparator = Comparator.builder().id(1L).creationDate(LocalDate.of(2026, 1, 1)).name("rest")
				.schema(Schema.GRAPHQL).build();

		ComparatorResponse response = ComparatorMapper.toResponse(comparator);

		assertEquals(new ComparatorResponse(1L, LocalDate.of(2026, 1, 1), "rest", Schema.GRAPHQL), response);
	}

	@Test
	void toComparatorIgnoresTheModifierIdsAndTheId() {
		Comparator comparator = ComparatorMapper.toComparator(new ComparatorCreationRequest("c", Schema.REST, Set.of(1L)));

		assertEquals("c", comparator.getName());
		assertEquals(Schema.REST, comparator.getSchema());
		assertTrue(comparator.getModifiers().isEmpty());
		assertNull(comparator.getId());
	}

	@Test
	void modifierResponsesCarryTheirFields() {
		Modifier modifier = Modifier.builder().id(3L).creationDate(LocalDate.of(2026, 2, 2)).target(HashTarget.BODY).description("d")
				.priority(5L).schema(Schema.REST).isAvailable(true).build();

		ModifierDetailedResponse detailed = ModifierMapper.toDetailedResponse(modifier);
		ModifierSummaryResponse summary = ModifierMapper.toSummaryResponse(modifier);

		assertEquals(new ModifierDetailedResponse(3L, LocalDate.of(2026, 2, 2), HashTarget.BODY, "d", 5L, Schema.REST, true), detailed);
		assertEquals(new ModifierSummaryResponse(3L, LocalDate.of(2026, 2, 2), HashTarget.BODY), summary);
	}

	@Test
	void toModifierTakesEveryRequestField() {
		Modifier modifier = ModifierMapper
				.toModifier(new ModifierCreationRequest(HashTarget.METHOD, "d", 2L, Schema.GRAPHQL, true));

		assertEquals(HashTarget.METHOD, modifier.getTarget());
		assertEquals("d", modifier.getDescription());
		assertEquals(2L, modifier.getPriority());
		assertEquals(Schema.GRAPHQL, modifier.getSchema());
		assertTrue(modifier.isAvailable());
	}

	@Test
	@KnownBug("BUG-028")
	void toModifierWithoutTheAvailabilityFlagDoesNotCrash() {
		Modifier modifier = ModifierMapper.toModifier(new ModifierCreationRequest(HashTarget.METHOD, "d", 2L, null, null));

		assertEquals(HashTarget.METHOD, modifier.getTarget());
	}

	@Test
	@KnownBug("BUG-029")
	void comparatorResponseListsItsModifiers() {
		// a client has no way to know which modifiers a comparator applies
		boolean hasModifiers = java.util.Arrays.stream(ComparatorResponse.class.getRecordComponents())
				.anyMatch(component -> component.getName().toLowerCase().contains("modifier"));

		assertTrue(hasModifiers);
	}
}
