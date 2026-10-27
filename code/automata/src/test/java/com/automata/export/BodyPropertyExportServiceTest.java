package com.automata.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.request.body.BodyPropertyRepository;
import com.automata.testsupport.KnownBug;

@ExtendWith(MockitoExtension.class)
class BodyPropertyExportServiceTest {

	@Mock
	BodyPropertyRepository repo;

	@InjectMocks
	BodyPropertyExportService service;

	@Test
	void exportNeedsAHostOrAProgram() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.export(null, null));

		assertTrue(ex.getMessage().contains("Either hostId or programId"));
		verifyNoInteractions(repo);
	}

	@Test
	void exportRefusesBothAHostAndAProgram() {
		assertThrows(IllegalArgumentException.class, () -> service.export(1L, 2L));
		verifyNoInteractions(repo);
	}

	@Test
	void everySegmentOfAFlattenedPathIsAName() {
		when(repo.findDistinctFullPathsByRequestHostId(1L)).thenReturn(List.of("user.address.city", "items[0]", "items[1].name", "username"));
		when(repo.findDistinctFullPathsByResponseHostId(1L)).thenReturn(List.of());

		BodyPropertyExportResult result = service.export(1L, null);

		assertEquals(List.of("user", "address", "city", "items", "name", "username"), result.names());
	}

	@Test
	void namesOfRequestsAndResponsesAreMergedWithoutDuplicates() {
		when(repo.findDistinctFullPathsByRequestProgramId(3L)).thenReturn(List.of("id", "user.name"));
		when(repo.findDistinctFullPathsByResponseProgramId(3L)).thenReturn(List.of("id", "token"));

		BodyPropertyExportResult result = service.export(null, 3L);

		assertEquals(List.of("id", "user", "name", "token"), result.names());
		verify(repo).findDistinctFullPathsByRequestProgramId(3L);
	}

	@Test
	void aRootArrayIsLeftOutAndItsMembersAreKept() {
		when(repo.findDistinctFullPathsByRequestHostId(1L)).thenReturn(List.of("[0].name", "[1].name"));
		when(repo.findDistinctFullPathsByResponseHostId(1L)).thenReturn(List.of());

		assertEquals(List.of("name"), service.export(1L, null).names());
	}

	@Test
	void nestedArraysKeepOnlyTheName() {
		when(repo.findDistinctFullPathsByRequestHostId(1L)).thenReturn(List.of("matrix[0][1]"));
		when(repo.findDistinctFullPathsByResponseHostId(1L)).thenReturn(List.of());

		assertEquals(List.of("matrix"), service.export(1L, null).names());
	}

	@Test
	void noPathsMeansNoNames() {
		when(repo.findDistinctFullPathsByRequestHostId(1L)).thenReturn(List.of());
		when(repo.findDistinctFullPathsByResponseHostId(1L)).thenReturn(List.of());

		assertTrue(service.export(1L, null).names().isEmpty());
	}

	@Test
	void theFilenameNamesTheKindAndTheId() {
		when(repo.findDistinctFullPathsByRequestHostId(1L)).thenReturn(List.of());
		when(repo.findDistinctFullPathsByResponseHostId(1L)).thenReturn(List.of());

		String filename = service.export(1L, null).filename();

		assertTrue(filename.startsWith("body-properties-host-1"), filename);
		assertTrue(filename.endsWith(".txt"), filename);
	}

	@Test
	@KnownBug("BUG-034")
	void theFilenameSeparatesTheIdFromTheTimestamp() {
		when(repo.findDistinctFullPathsByRequestHostId(5L)).thenReturn(List.of());
		when(repo.findDistinctFullPathsByResponseHostId(5L)).thenReturn(List.of());

		// today: body-properties-host-52026-10-27T10:15:30.123Z.txt, host 5 and year 2026 run together
		assertTrue(service.export(5L, null).filename().matches("body-properties-host-5[-_.].*"));
	}

	@Test
	@KnownBug("BUG-034")
	void theFilenameHasNoColons() {
		when(repo.findDistinctFullPathsByRequestHostId(5L)).thenReturn(List.of());
		when(repo.findDistinctFullPathsByResponseHostId(5L)).thenReturn(List.of());

		assertTrue(!service.export(5L, null).filename().contains(":"));
	}

	@Test
	@KnownBug("BUG-035")
	void bracketFormFieldsExportTheirInnerName() {
		when(repo.findDistinctFullPathsByRequestHostId(1L)).thenReturn(List.of("filter[name]"));
		when(repo.findDistinctFullPathsByResponseHostId(1L)).thenReturn(List.of());

		assertEquals(List.of("filter", "name"), service.export(1L, null).names());
	}

	@Test
	@KnownBug("BUG-035")
	void aKeyWithADotInItDoesNotProduceBrokenNames() {
		// json-flattener writes a key that contains a dot as ["a.b"]
		when(repo.findDistinctFullPathsByRequestHostId(1L)).thenReturn(List.of("[\"a.b\"]"));
		when(repo.findDistinctFullPathsByResponseHostId(1L)).thenReturn(List.of());

		assertTrue(service.export(1L, null).names().stream().noneMatch(n -> n.contains("\"") || n.contains("]")));
	}
}
