package com.automata.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.automata.program.common.dto.PatchProgramRequest;
import com.automata.program.common.enums.Platform;
import com.automata.testsupport.KnownBug;
import com.automata.vulnerability.Vulnerability;
import com.automata.vulnerability.VulnerabilityRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class ProgramServiceTest {

	@Mock
	ProgramRepository programRepo;

	@Mock
	VulnerabilityRepository vulnerabilityRepo;

	@InjectMocks
	ProgramService service;

	private Program program() {
		Program program = new Program();
		program.setId(1L);
		program.setName("Acme");
		program.setLink("https://acme.test");
		program.setPlatform(Platform.HACKERONE);
		program.setProgramRateLimit(50);
		return program;
	}

	private static Vulnerability vuln(long id) {
		Vulnerability vulnerability = new Vulnerability();
		vulnerability.setId(id);
		vulnerability.setName("v" + id);
		return vulnerability;
	}

	private static PatchProgramRequest patch(String name, String link, Integer rate, Platform platform, Set<Long> vulnIds,
			boolean linkNull, boolean platformNull) {
		return new PatchProgramRequest(name, link, rate, platform, vulnIds, linkNull, platformNull);
	}

	// ---- getProgramWithVulns ----

	@Test
	void getProgramWithVulnsReturnsTheProgram() {
		Program program = program();
		when(programRepo.findByIdWithVulns(1L)).thenReturn(Optional.of(program));

		assertSame(program, service.getProgramWithVulns(1L));
	}

	@Test
	void getProgramWithVulnsOfUnknownIdIsNotFound() {
		when(programRepo.findByIdWithVulns(9L)).thenReturn(Optional.empty());

		EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.getProgramWithVulns(9L));
		assertEquals("Program not found", ex.getMessage());
	}

	// ---- createProgram / list ----

	@Test
	void createProgramSavesAndReturnsTheSavedProgram() {
		Program toSave = program();
		Program saved = program();
		when(programRepo.save(toSave)).thenReturn(saved);

		assertSame(saved, service.createProgram(toSave));
	}

	@Test
	void listDelegatesTheQueryAndThePageableToTheRepository() {
		Pageable pageable = PageRequest.of(2, 5);
		Page<Program> page = new PageImpl<>(List.of(program()));
		when(programRepo.findByNameContainingIgnoreCase("ac", pageable)).thenReturn(page);

		assertSame(page, service.getProgramsPagedAndFilteredOnQueryString("ac", pageable));
	}

	// ---- patchProgram ----

	@Test
	void patchOfUnknownProgramIsNotFoundAndSavesNothing() {
		when(programRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class,
				() -> service.patchProgram(9L, patch("x", null, null, null, null, false, false)));
		verify(programRepo, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void emptyPatchChangesNothing() {
		Program program = program();
		when(programRepo.findById(1L)).thenReturn(Optional.of(program));

		service.patchProgram(1L, patch(null, null, null, null, null, false, false));

		assertEquals("Acme", program.getName());
		assertEquals("https://acme.test", program.getLink());
		assertEquals(Platform.HACKERONE, program.getPlatform());
		assertEquals(50, program.getProgramRateLimit());
		verifyNoInteractions(vulnerabilityRepo);
		verify(programRepo).save(program);
	}

	@Test
	void patchChangesNameLinkAndPlatform() {
		Program program = program();
		when(programRepo.findById(1L)).thenReturn(Optional.of(program));

		service.patchProgram(1L, patch("Renamed", "https://new.test", null, Platform.BUGCROWD, null, false, false));

		assertEquals("Renamed", program.getName());
		assertEquals("https://new.test", program.getLink());
		assertEquals(Platform.BUGCROWD, program.getPlatform());
	}

	@Test
	void setLinkNullClearsTheLinkEvenWhenANewLinkIsGiven() {
		Program program = program();
		when(programRepo.findById(1L)).thenReturn(Optional.of(program));

		service.patchProgram(1L, patch(null, "https://ignored.test", null, null, null, true, false));

		assertNull(program.getLink());
	}

	@Test
	void setPlatformNullClearsThePlatformEvenWhenANewPlatformIsGiven() {
		Program program = program();
		when(programRepo.findById(1L)).thenReturn(Optional.of(program));

		service.patchProgram(1L, patch(null, null, null, Platform.BUGCROWD, null, false, true));

		assertNull(program.getPlatform());
	}

	@Test
	void patchReplacesTheOutOfScopeVulnerabilities() {
		Program program = program();
		program.getOutOfScopeVulns().add(vuln(1));
		when(programRepo.findById(1L)).thenReturn(Optional.of(program));
		when(vulnerabilityRepo.findAllById(Set.of(2L, 3L))).thenReturn(List.of(vuln(2), vuln(3)));

		service.patchProgram(1L, patch(null, null, null, null, Set.of(2L, 3L), false, false));

		assertEquals(Set.of(2L, 3L),
				program.getOutOfScopeVulns().stream().map(Vulnerability::getId).collect(java.util.stream.Collectors.toSet()));
	}

	@Test
	void patchWithAnEmptyVulnerabilitySetClearsThem() {
		Program program = program();
		program.getOutOfScopeVulns().add(vuln(1));
		when(programRepo.findById(1L)).thenReturn(Optional.of(program));
		when(vulnerabilityRepo.findAllById(Set.of())).thenReturn(List.of());

		service.patchProgram(1L, patch(null, null, null, null, Set.of(), false, false));

		assertEquals(0, program.getOutOfScopeVulns().size());
	}

	@Test
	@KnownBug("BUG-001")
	void patchChangesTheProgramRateLimit() {
		Program program = program();
		when(programRepo.findById(1L)).thenReturn(Optional.of(program));

		service.patchProgram(1L, patch(null, null, 80, null, null, false, false));

		assertEquals(80, program.getProgramRateLimit());
	}

	@Test
	@KnownBug("BUG-002")
	void patchWithAnUnknownVulnerabilityIdIsNotFound() {
		Program program = program();
		when(programRepo.findById(1L)).thenReturn(Optional.of(program));
		when(vulnerabilityRepo.findAllById(Set.of(2L, 99L))).thenReturn(List.of(vuln(2)));

		assertThrows(EntityNotFoundException.class,
				() -> service.patchProgram(1L, patch(null, null, null, null, Set.of(2L, 99L), false, false)));
	}

	// ---- deleteProgram ----

	@Test
	void deleteIsNotImplementedYet() {
		RuntimeException ex = assertThrows(RuntimeException.class, () -> service.deleteProgram(1L));
		assertEquals("Delete operation is not implemented yet", ex.getMessage());
	}

	// GlobalExceptionHandler answers 501 for an UnsupportedOperationException and 500 for any other exception
	@Test
	@KnownBug("BUG-003")
	void deleteSignalsNotImplementedWithUnsupportedOperationException() {
		assertThrows(UnsupportedOperationException.class, () -> service.deleteProgram(1L));
	}
}
