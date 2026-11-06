package com.automata.routine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.routine.common.dto.RoutinePatchRequest;
import com.automata.vulnerability.VulnerabilityRepository;

@ExtendWith(MockitoExtension.class)
class RoutineServicePatchFlagsTest {

	@Mock
	RoutineRepository routineRepo;
	@Mock
	VulnerabilityRepository vulnRepo;

	@InjectMocks
	RoutineService service;

	private Routine routine() {
		Routine r = new Routine();
		r.setId(1L);
		when(routineRepo.findById(1L)).thenReturn(Optional.of(r));
		when(routineRepo.save(r)).thenReturn(r);
		return r;
	}

	private RoutinePatchRequest patch(Optional<Boolean> available, Optional<Boolean> wordlistRequired) {
		return new RoutinePatchRequest(Optional.empty(), false, available, wordlistRequired, Optional.empty(),
				Optional.empty(), null, Optional.empty(), false, Optional.empty(), false);
	}

	@Test
	void patchSetsTheWordlistRequirement() {
		Routine r = routine();

		service.patchRoutine(1L, patch(Optional.empty(), Optional.of(true)));

		assertTrue(r.isWordlistRequired());
	}

	@Test
	void patchClearsTheWordlistRequirement() {
		Routine r = routine();
		r.setWordlistRequired(true);

		service.patchRoutine(1L, patch(Optional.empty(), Optional.of(false)));

		assertFalse(r.isWordlistRequired());
	}

	@Test
	void patchWithoutTheWordlistRequirementKeepsIt() {
		Routine r = routine();
		r.setWordlistRequired(true);

		service.patchRoutine(1L, patch(Optional.of(true), Optional.empty()));

		assertTrue(r.isWordlistRequired());
		assertTrue(r.isAvailableAtConsumer());
	}

}
