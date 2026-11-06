package com.automata.request.comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.automata.request.comparator.common.dto.ModifierPatchRequest;
import com.automata.request.comparator.hash.HashTarget;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class ModifierServiceTest {

	@Mock
	ModifierRepository modifierRepo;

	@InjectMocks
	ModifierService service;

	private static Modifier modifier() {
		return Modifier.builder().id(1L).target(HashTarget.PATH).priority(1L).description("path").isAvailable(true).build();
	}

	@Test
	void getReturnsTheModifier() {
		Modifier modifier = modifier();
		when(modifierRepo.findById(1L)).thenReturn(Optional.of(modifier));

		assertSame(modifier, service.getModifierById(1L));
	}

	@Test
	void getOfUnknownModifierIsNotFound() {
		when(modifierRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getModifierById(9L));
	}

	@Test
	void createSavesTheModifier() {
		Modifier modifier = modifier();
		when(modifierRepo.save(modifier)).thenReturn(modifier);

		assertSame(modifier, service.createModifier(modifier));
	}

	@Test
	@SuppressWarnings("unchecked")
	void listUsesASpecification() {
		var page = new PageImpl<>(List.of(modifier()));
		when(modifierRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

		assertSame(page, service.getModifiersPagedAndFilteredOnHashTargetAndSchema(HashTarget.PATH, null, PageRequest.of(0, 5)));
	}

	@Test
	void patchChangesDescriptionAndAvailability() {
		Modifier modifier = modifier();
		when(modifierRepo.findById(1L)).thenReturn(Optional.of(modifier));

		service.patchModifier(1L, new ModifierPatchRequest(Optional.of("new"), Optional.of(false)));

		assertEquals("new", modifier.getDescription());
		assertFalse(modifier.isAvailable());
		verify(modifierRepo).save(modifier);
	}

	@Test
	void emptyPatchChangesNothing() {
		Modifier modifier = modifier();
		when(modifierRepo.findById(1L)).thenReturn(Optional.of(modifier));

		service.patchModifier(1L, new ModifierPatchRequest(Optional.empty(), Optional.empty()));

		assertEquals("path", modifier.getDescription());
		assertTrue(modifier.isAvailable());
	}

	@Test
	void patchOfUnknownModifierIsNotFound() {
		when(modifierRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class,
				() -> service.patchModifier(9L, new ModifierPatchRequest(Optional.empty(), Optional.empty())));
	}

	@Test
	void deleteRemovesTheModifier() {
		Modifier modifier = modifier();
		when(modifierRepo.findById(1L)).thenReturn(Optional.of(modifier));

		service.deleteModifier(1L);

		verify(modifierRepo).delete(modifier);
	}

	@Test
	void deleteOfUnknownModifierIsNotFound() {
		when(modifierRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.deleteModifier(9L));
	}
}
