package com.automata.routine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
class RoutineServicePatchDescriptionsTest {

	@Mock
	RoutineRepository routineRepo;
	@Mock
	VulnerabilityRepository vulnRepo;

	@InjectMocks
	RoutineService service;

	private Routine routine() {
		Routine r = new Routine();
		r.setId(1L);
		r.setDescription("old description");
		r.setUsageDescriptionForAgent("old usage");
		when(routineRepo.findById(1L)).thenReturn(Optional.of(r));
		lenient().when(routineRepo.save(r)).thenReturn(r);
		return r;
	}

	private static RoutinePatchRequest patch(Optional<String> description, Optional<String> usage) {
		return new RoutinePatchRequest(description, usage, Optional.empty(), Optional.empty(), null, Optional.empty(), false,
				Optional.empty(), false);
	}

	@Test
	void patchChangesBothDescriptions() {
		Routine r = routine();

		service.patchRoutine(1L, patch(Optional.of("new description"), Optional.of("new usage")));

		assertEquals("new description", r.getDescription());
		assertEquals("new usage", r.getUsageDescriptionForAgent());
	}

	@Test
	void patchWithoutTheDescriptionsKeepsThem() {
		Routine r = routine();

		service.patchRoutine(1L, patch(Optional.empty(), Optional.empty()));

		assertEquals("old description", r.getDescription());
		assertEquals("old usage", r.getUsageDescriptionForAgent());
	}

	@Test
	void aBlankDescriptionIsRefused() {
		Routine r = routine();

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.patchRoutine(1L, patch(Optional.of("  "), Optional.empty())));

		assertEquals("description must not be blank", e.getMessage());
		assertEquals("old description", r.getDescription());
		verify(routineRepo, never()).save(any());
	}

	@Test
	void aBlankUsageDescriptionIsRefused() {
		Routine r = routine();

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.patchRoutine(1L, patch(Optional.empty(), Optional.of(""))));

		assertEquals("usageDescriptionForAgent must not be blank", e.getMessage());
		assertEquals("old usage", r.getUsageDescriptionForAgent());
		verify(routineRepo, never()).save(any());
	}

	@Test
	void aUsageDescriptionOverTheLimitIsRefused() {
		Routine r = routine();
		String tooLong = "u".repeat(Routine.MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH + 1);

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.patchRoutine(1L, patch(Optional.empty(), Optional.of(tooLong))));

		assertEquals("usageDescriptionForAgent is too long, the limit is 5000 characters", e.getMessage());
		assertEquals("old usage", r.getUsageDescriptionForAgent());
	}

	@Test
	void aUsageDescriptionAtTheLimitIsAccepted() {
		Routine r = routine();
		String atLimit = "u".repeat(Routine.MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH);

		service.patchRoutine(1L, patch(Optional.empty(), Optional.of(atLimit)));

		assertEquals(atLimit, r.getUsageDescriptionForAgent());
	}

	@Test
	void requireValidUsageDescriptionForAgentAcceptsAValueAtTheLimitAndRefusesBlankOrLonger() {
		assertDoesNotThrow(() -> RoutineService.requireValidUsageDescriptionForAgent("u".repeat(5000)));
		assertThrows(IllegalArgumentException.class, () -> RoutineService.requireValidUsageDescriptionForAgent(" "));
		assertThrows(IllegalArgumentException.class, () -> RoutineService.requireValidUsageDescriptionForAgent("u".repeat(5001)));
	}

}
