package com.automata.request.comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.automata.request.comparator.common.dto.ComparatorPatchRequest;
import com.automata.request.comparator.common.enums.Schema;
import com.automata.request.comparator.hash.HashTarget;
import com.automata.testsupport.KnownBug;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class ComparatorServiceTest {

	@Mock
	ComparatorRepository comparatorRepo;

	@Mock
	ModifierRepository modifierRepo;

	@InjectMocks
	ComparatorService service;

	private static Comparator comparator() {
		return Comparator.builder().id(1L).name("rest").schema(Schema.REST).build();
	}

	private static Modifier modifier(long id) {
		return Modifier.builder().id(id).target(HashTarget.PATH).priority(id).description("d").build();
	}

	@Test
	void getReturnsTheComparator() {
		Comparator comparator = comparator();
		when(comparatorRepo.findById(1L)).thenReturn(Optional.of(comparator));

		assertSame(comparator, service.getComparatorById(1L));
	}

	@Test
	void getOfUnknownComparatorIsNotFound() {
		when(comparatorRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getComparatorById(9L));
	}

	@Test
	@SuppressWarnings("unchecked")
	void listUsesASpecificationAndThePageable() {
		var page = new PageImpl<>(List.of(comparator()));
		when(comparatorRepo.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

		assertSame(page, service.getComparatorsPagedAndFiltered("re", Schema.REST, PageRequest.of(0, 5)));
	}

	@Test
	void createAttachesTheModifiersThatExist() {
		Comparator toCreate = Comparator.builder().name("c").build();
		when(modifierRepo.findAllById(Set.of(1L, 2L))).thenReturn(List.of(modifier(1), modifier(2)));
		when(comparatorRepo.save(toCreate)).thenReturn(toCreate);

		Comparator created = service.createComparator(toCreate, Set.of(1L, 2L));

		assertEquals(2, created.getModifiers().size());
	}

	@Test
	@KnownBug("BUG-027")
	void createWithAnUnknownModifierIdIsNotFound() {
		Comparator toCreate = Comparator.builder().name("c").build();
		when(modifierRepo.findAllById(Set.of(1L, 99L))).thenReturn(List.of(modifier(1)));

		assertThrows(EntityNotFoundException.class, () -> service.createComparator(toCreate, Set.of(1L, 99L)));
	}

	@Test
	@KnownBug("BUG-027")
	void createWithoutAnyModifierIsRefused() {
		// equalizing with a comparator without modifiers fails (Guava cannot combine zero hash codes)
		Comparator toCreate = Comparator.builder().name("c").build();
		when(modifierRepo.findAllById(Set.of())).thenReturn(List.of());

		assertThrows(IllegalArgumentException.class, () -> service.createComparator(toCreate, Set.of()));
	}

	@Test
	void patchChangesNameAndSchema() {
		Comparator comparator = comparator();
		when(comparatorRepo.findById(1L)).thenReturn(Optional.of(comparator));

		service.patchComparator(1L, new ComparatorPatchRequest(Optional.of("new"), Optional.of(Schema.GRAPHQL), false));

		assertEquals("new", comparator.getName());
		assertEquals(Schema.GRAPHQL, comparator.getSchema());
		verify(comparatorRepo).save(comparator);
	}

	@Test
	void setSchemaNullClearsTheSchemaAndWinsOverAGivenSchema() {
		Comparator comparator = comparator();
		when(comparatorRepo.findById(1L)).thenReturn(Optional.of(comparator));

		service.patchComparator(1L, new ComparatorPatchRequest(Optional.empty(), Optional.of(Schema.GRAPHQL), true));

		assertNull(comparator.getSchema());
		assertEquals("rest", comparator.getName());
	}

	@Test
	void emptyPatchChangesNothing() {
		Comparator comparator = comparator();
		when(comparatorRepo.findById(1L)).thenReturn(Optional.of(comparator));

		service.patchComparator(1L, new ComparatorPatchRequest(Optional.empty(), Optional.empty(), false));

		assertEquals("rest", comparator.getName());
		assertEquals(Schema.REST, comparator.getSchema());
	}

	@Test
	void patchOfUnknownComparatorIsNotFound() {
		when(comparatorRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class,
				() -> service.patchComparator(9L, new ComparatorPatchRequest(Optional.empty(), Optional.empty(), false)));
	}

	@Test
	void deleteRemovesTheComparator() {
		Comparator comparator = comparator();
		when(comparatorRepo.findById(1L)).thenReturn(Optional.of(comparator));

		service.deleteComparator(1L);

		verify(comparatorRepo).delete(comparator);
	}

	@Test
	void deleteOfUnknownComparatorIsNotFound() {
		when(comparatorRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.deleteComparator(9L));
	}
}
