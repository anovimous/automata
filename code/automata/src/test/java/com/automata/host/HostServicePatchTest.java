package com.automata.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.automata.program.ProgramRepository;

@ExtendWith(MockitoExtension.class)
class HostServicePatchTest {

	@Mock
	HostRepository hostRepo;

	@Mock
	ProgramRepository programRepo;

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

}
