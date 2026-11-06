package com.automata.request.equalityset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.host.HostRepository;
import com.automata.request.RequestRepository;
import com.automata.request.comparator.ComparatorRepository;
import com.automata.request.comparator.hash.HashStrategyResolver;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class RequestEqualityServiceEqualitySetTest {

	@Mock
	EqualizationRepository equalizationRepo;

	@Mock
	RequestRepository requestRepo;

	@Mock
	HostRepository hostRepo;

	@Mock
	ComparatorRepository comparatorRepo;

	@Mock
	RequestEqualitySetRepository equalitySetRepo;

	@Mock
	RequestEqualityRelationRepository requestEqualityRelationRepo;

	@Mock
	HashStrategyResolver resolver;

	@InjectMocks
	RequestEqualityService service;

	private RequestEqualitySet setWithDefaults() {
		Equalization equalization = Equalization.builder().id(1L).build();
		return RequestEqualitySet.builder()
				.id(1L)
				.alias("GET /api/users")
				.numberOfRequests(5)
				.equalization(equalization)
				.build();
	}

	@Test
	void getEqualitySetReturnsTheSet() {
		RequestEqualitySet set = setWithDefaults();
		when(equalitySetRepo.findById(1L)).thenReturn(Optional.of(set));

		RequestEqualitySet result = service.getEqualitySet(1L);

		assertEquals(1L, result.getId());
		assertEquals("GET /api/users", result.getAlias());
	}

	@Test
	void getEqualitySetThrowsWhenNotFound() {
		when(equalitySetRepo.findById(99L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getEqualitySet(99L));
	}

	@Test
	void patchAutoApprovalSetsTheFlag() {
		RequestEqualitySet set = setWithDefaults();
		assertFalse(set.isAutoApproval());
		when(equalitySetRepo.findById(1L)).thenReturn(Optional.of(set));
		when(equalitySetRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.patchEqualitySet(1L, null, true);

		assertTrue(set.isAutoApproval());
	}

	@Test
	void patchAliasSetsTheAlias() {
		RequestEqualitySet set = setWithDefaults();
		when(equalitySetRepo.findById(1L)).thenReturn(Optional.of(set));
		when(equalitySetRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.patchEqualitySet(1L, "POST /api/orders", null);

		assertEquals("POST /api/orders", set.getAlias());
	}

	@Test
	void patchWithNullFieldsDoesNotOverwrite() {
		RequestEqualitySet set = setWithDefaults();
		set.setAutoApproval(true);
		when(equalitySetRepo.findById(1L)).thenReturn(Optional.of(set));
		when(equalitySetRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.patchEqualitySet(1L, null, null);

		assertTrue(set.isAutoApproval());
		assertEquals("GET /api/users", set.getAlias());
	}

	@Test
	void patchBothFieldsAtOnce() {
		RequestEqualitySet set = setWithDefaults();
		when(equalitySetRepo.findById(1L)).thenReturn(Optional.of(set));
		when(equalitySetRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.patchEqualitySet(1L, "New alias", true);

		assertEquals("New alias", set.getAlias());
		assertTrue(set.isAutoApproval());
	}

	@Test
	void patchThrowsWhenSetNotFound() {
		when(equalitySetRepo.findById(99L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.patchEqualitySet(99L, null, true));
	}

	@Test
	void autoApprovalDefaultsToFalse() {
		RequestEqualitySet set = RequestEqualitySet.builder()
				.alias("test")
				.equalization(Equalization.builder().id(1L).build())
				.numberOfRequests(1)
				.build();

		assertFalse(set.isAutoApproval());
	}

}
