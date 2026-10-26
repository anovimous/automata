package com.automata.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.host.common.dto.PatchHostRequest;
import com.automata.host.common.enums.Scope;
import com.automata.program.Program;
import com.automata.program.ProgramRepository;

@ExtendWith(MockitoExtension.class)
class HostServicePatchTest {

	@Mock
	HostRepository hostRepo;

	@Mock
	ProgramRepository programRepo;

	@Mock
	com.automata.job.repository.NarrowHttpJobRepository narrowJobRepo;

	@Mock
	com.automata.job.repository.WideHttpJobRepository wideJobRepo;

	@InjectMocks
	HostService service;

	private Host hostWithDefaults() {
		Host host = new Host();
		host.setId(1L);
		host.setHost("api.example.com");
		host.setLevel(1);
		host.setScope(Scope.FQDN);
		when(hostRepo.findById(1L)).thenReturn(Optional.of(host));
		return host;
	}

	@Test
	void patchDescriptionSetsTheField() {
		Host host = hostWithDefaults();

		service.patchHost(1L, new PatchHostRequest(null, null, null, null, null,
				"Main API backend", null, null));

		assertEquals("Main API backend", host.getDescription());
	}

	@Test
	void patchTechnologiesSetsTheList() {
		Host host = hostWithDefaults();

		List<String> technologies = List.of("nginx", "spring-boot", "postgresql");

		service.patchHost(1L, new PatchHostRequest(null, null, null, null, null,
				null, technologies, null));

		assertEquals(technologies, host.getTechnologies());
	}

	@Test
	void patchNotesSetsTheField() {
		Host host = hostWithDefaults();

		service.patchHost(1L, new PatchHostRequest(null, null, null, null, null,
				null, null, "Requires bearer token auth"));

		assertEquals("Requires bearer token auth", host.getNotes());
	}

	@Test
	void patchAllNewFieldsAtOnce() {
		Host host = hostWithDefaults();

		List<String> technologies = List.of("react", "node");

		service.patchHost(1L, new PatchHostRequest(null, null, null, null, null,
				"Frontend app", technologies, "SPA with API calls"));

		assertEquals("Frontend app", host.getDescription());
		assertEquals(technologies, host.getTechnologies());
		assertEquals("SPA with API calls", host.getNotes());
	}

	@Test
	void patchWithNullNewFieldsDoesNotOverwriteExistingValues() {
		Host host = hostWithDefaults();
		host.setDescription("Existing description");
		host.setTechnologies(List.of("nginx"));
		host.setNotes("Existing notes");

		service.patchHost(1L, new PatchHostRequest(null, null, null, null, null,
				null, null, null));

		assertEquals("Existing description", host.getDescription());
		assertEquals(List.of("nginx"), host.getTechnologies());
		assertEquals("Existing notes", host.getNotes());
	}

	@Test
	void patchNewFieldsDoesNotAffectExistingFields() {
		Host host = hostWithDefaults();
		host.setScope(Scope.WILDCARD);

		service.patchHost(1L, new PatchHostRequest(null, null, null, null, null,
				"A description", null, null));

		assertEquals(Scope.WILDCARD, host.getScope());
		assertEquals("A description", host.getDescription());
	}

	@Test
	void newFieldsAreNullByDefault() {
		Host host = new Host();

		assertNull(host.getDescription());
		assertNull(host.getTechnologies());
		assertNull(host.getNotes());
	}

	// ---- rate limits

	private static final java.util.Set<com.automata.job.domain.model.enums.JobState> IN_PROGRESS = java.util.EnumSet.of(
			com.automata.job.domain.model.enums.JobState.TOQUEUE, com.automata.job.domain.model.enums.JobState.SCHEDULED,
			com.automata.job.domain.model.enums.JobState.QUEUED, com.automata.job.domain.model.enums.JobState.RUNNING,
			com.automata.job.domain.model.enums.JobState.PAUSED);

	private Host hostWithLimits(int host, int shortLimit, int longLimit) {
		Host h = hostWithDefaults();
		h.setHostRateLimit(host);
		h.setShortRateLimit(shortLimit);
		h.setLongRateLimit(longLimit);
		Program program = new Program();
		program.setId(7L);
		h.setProgram(program);
		return h;
	}

	private PatchHostRequest limits(Integer host, Integer shortLimit, Integer longLimit) {
		return new PatchHostRequest(null, null, host, shortLimit, longLimit, null, null, null);
	}

	@Test
	void rateLimitsAreChangedWhenNoJobIsInProgressOnTheHost() {
		Host host = hostWithLimits(10, 4, 4);
		when(narrowJobRepo.existsByHostIdAndCurrentStateIn(1L, IN_PROGRESS)).thenReturn(false);
		when(wideJobRepo.existsByProgramIdAndCurrentStateIn(7L, IN_PROGRESS)).thenReturn(false);

		service.patchHost(1L, limits(50, 20, 30));

		assertEquals(50, host.getHostRateLimit());
		assertEquals(20, host.getShortRateLimit());
		assertEquals(30, host.getLongRateLimit());
		verify(hostRepo).save(host);
	}

	@Test
	void aSingleLimitCanBeChangedKeepingTheOthers() {
		Host host = hostWithLimits(10, 4, 4);

		service.patchHost(1L, limits(20, null, null));

		assertEquals(20, host.getHostRateLimit());
		assertEquals(4, host.getShortRateLimit());
		assertEquals(4, host.getLongRateLimit());
	}

	@Test
	void aHostWithoutALimitCanBeOpened() {
		Host host = hostWithLimits(0, 0, 0);

		service.patchHost(1L, limits(10, 5, 5));

		assertEquals(10, host.getHostRateLimit());
	}

	@Test
	void rateLimitsDoNotChangeWhileANarrowJobOfTheHostIsInProgress() {
		Host host = hostWithLimits(10, 4, 4);
		when(narrowJobRepo.existsByHostIdAndCurrentStateIn(1L, IN_PROGRESS)).thenReturn(true);

		assertThrows(com.automata.host.common.exception.HostInUseException.class,
				() -> service.patchHost(1L, limits(50, 20, 30)));

		assertEquals(10, host.getHostRateLimit());
		verify(hostRepo, never()).save(any());
	}

	@Test
	void rateLimitsDoNotChangeWhileAWideJobOfTheProgramIsInProgress() {
		Host host = hostWithLimits(10, 4, 4);
		when(wideJobRepo.existsByProgramIdAndCurrentStateIn(7L, IN_PROGRESS)).thenReturn(true);

		assertThrows(com.automata.host.common.exception.HostInUseException.class,
				() -> service.patchHost(1L, limits(50, 20, 30)));

		assertEquals(10, host.getHostRateLimit());
		verify(hostRepo, never()).save(any());
	}

	@Test
	void patchingTheSameLimitsDoesNotLookAtTheJobs() {
		hostWithLimits(10, 4, 4);

		service.patchHost(1L, limits(10, 4, 4));

		verifyNoInteractions(narrowJobRepo, wideJobRepo);
	}

	@Test
	void patchingOtherFieldsDoesNotLookAtTheJobs() {
		Host host = hostWithLimits(10, 4, 4);

		service.patchHost(1L, new PatchHostRequest(null, null, null, null, null, "new description", null, null));

		assertEquals("new description", host.getDescription());
		verifyNoInteractions(narrowJobRepo, wideJobRepo);
	}

	@Test
	void incompatibleLimitsAreRefusedBeforeLookingAtTheJobs() {
		hostWithLimits(10, 4, 4);

		assertThrows(IllegalArgumentException.class, () -> service.patchHost(1L, limits(5, null, null)));

		verifyNoInteractions(narrowJobRepo, wideJobRepo);
	}

	@Test
	void negativeLimitsAreRefused() {
		hostWithLimits(10, 4, 4);

		assertThrows(IllegalArgumentException.class, () -> service.patchHost(1L, limits(null, -1, null)));
		assertThrows(IllegalArgumentException.class, () -> service.patchHost(1L, limits(-5, null, null)));
	}

}
