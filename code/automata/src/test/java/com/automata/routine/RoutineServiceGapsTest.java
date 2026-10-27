package com.automata.routine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.automata.routine.common.enums.ResultContentType;
import com.automata.testsupport.KnownBug;
import com.automata.vulnerability.Vulnerability;
import com.automata.vulnerability.VulnerabilityRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class RoutineServiceGapsTest {

	@Mock
	RoutineRepository routineRepo;

	@Mock
	VulnerabilityRepository vulnRepo;

	@InjectMocks
	RoutineService service;

	@Test
	void getReturnsTheRoutine() {
		Routine routine = new Routine();
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));

		assertSame(routine, service.getRoutineById(1L));
	}

	@Test
	void getOfUnknownRoutineIsNotFound() {
		when(routineRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getRoutineById(9L));
	}

	@Test
	void listFiltersOnTheKey() {
		var page = new PageImpl<>(List.of(new Routine()));
		when(routineRepo.findByKeyContainingIgnoreCase("fu", PageRequest.of(0, 5))).thenReturn(page);

		assertSame(page, service.getRoutinesPagedAndFilteredOnQueryString("fu", PageRequest.of(0, 5)));
	}

	@Test
	void listOfAVulnerabilityNeedsTheVulnerabilityToExist() {
		when(vulnRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class,
				() -> service.getVulnerabilityRoutinesPagedAndFilteredOnQueryString(9L, "", PageRequest.of(0, 5)));
	}

	@Test
	void listOfAVulnerabilityFiltersOnTheKeyAndTheVulnerability() {
		Vulnerability vulnerability = new Vulnerability();
		var page = new PageImpl<>(List.of(new Routine()));
		when(vulnRepo.findById(1L)).thenReturn(Optional.of(vulnerability));
		when(routineRepo.findByKeyContainingIgnoreCaseAndVulnerability("fu", vulnerability, PageRequest.of(0, 5))).thenReturn(page);

		assertSame(page, service.getVulnerabilityRoutinesPagedAndFilteredOnQueryString(1L, "fu", PageRequest.of(0, 5)));
	}

	@Test
	void createWithAVulnerabilityLinksIt() {
		Routine routine = new Routine();
		routine.setResultContentType(ResultContentType.TEXT);
		Vulnerability vulnerability = new Vulnerability();
		when(vulnRepo.findById(1L)).thenReturn(Optional.of(vulnerability));
		when(routineRepo.save(routine)).thenReturn(routine);

		assertSame(vulnerability, service.createRoutine(routine, 1L).getVulnerability());
	}

	@Test
	void createWithAnUnknownVulnerabilityIsNotFound() {
		Routine routine = new Routine();
		routine.setResultContentType(ResultContentType.TEXT);
		when(vulnRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.createRoutine(routine, 9L));
	}

	@Test
	void createWithoutAResultContentTypeIsRefused() {
		assertEquals("resultContentType is required",
				assertThrows(IllegalArgumentException.class, () -> service.createRoutine(new Routine(), null)).getMessage());
	}

	@Test
	@KnownBug("BUG-003")
	void deleteSignalsNotImplementedWithUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> service.deleteRoutine(1L));
	}
}
