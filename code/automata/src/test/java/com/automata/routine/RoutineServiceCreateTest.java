package com.automata.routine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.routine.common.enums.ResultContentType;
import com.automata.vulnerability.VulnerabilityRepository;

@ExtendWith(MockitoExtension.class)
class RoutineServiceCreateTest {

	@Mock
	RoutineRepository routineRepo;
	@Mock
	VulnerabilityRepository vulnRepo;

	@InjectMocks
	RoutineService service;

	@Test
	void routineWithAResultContentTypeIsSaved() {
		Routine routine = new Routine();
		routine.setResultContentType(ResultContentType.JSON);
		when(routineRepo.save(routine)).thenReturn(routine);

		assertSame(routine, service.createRoutine(routine, null));

		verify(routineRepo).save(routine);
	}

	@Test
	void aRoutineKeyThatExistsIsRefusedAndNothingIsSaved() {
		Routine routine = new Routine();
		routine.setKey("fuzz");
		routine.setResultContentType(ResultContentType.JSON);
		when(routineRepo.existsByKey("fuzz")).thenReturn(true);

		com.automata.common.exception.AlreadyExistsException e = org.junit.jupiter.api.Assertions
				.assertThrows(com.automata.common.exception.AlreadyExistsException.class, () -> service.createRoutine(routine, null));

		assertEquals("Routine 'fuzz' already exists", e.getMessage());
		org.mockito.Mockito.verify(routineRepo, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void aNewRoutineKeyIsSaved() {
		Routine routine = new Routine();
		routine.setKey("masser");
		routine.setResultContentType(ResultContentType.JSON);
		when(routineRepo.existsByKey("masser")).thenReturn(false);
		when(routineRepo.save(routine)).thenReturn(routine);

		assertSame(routine, service.createRoutine(routine, null));
	}

	@Test
	void routineWithoutAResultContentTypeIsRefused() {
		Routine routine = new Routine();

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.createRoutine(routine, null));

		assertEquals("resultContentType is required", e.getMessage());
		verifyNoInteractions(routineRepo, vulnRepo);
	}

}
