package com.automata.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.host.common.dto.HostCreationRequest;
import com.automata.program.Program;
import com.automata.program.ProgramRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class HostServiceCreateTest {

	@Mock
	HostRepository hostRepo;
	@Mock
	ProgramRepository programRepo;

	@InjectMocks
	HostService service;

	private HostCreationRequest request(Integer host, Integer shortLimit, Integer longLimit) {
		return new HostCreationRequest("api.example.com", null, host, shortLimit, longLimit, 1L);
	}

	private Host toBeCreated() {
		Host host = new Host();
		host.setHost("api.example.com");
		return host;
	}

	@Test
	void theRateLimitsOfTheRequestAreStoredOnTheNewHost() {
		when(programRepo.findById(1L)).thenReturn(Optional.of(new Program()));
		when(hostRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Host created = service.createHost(request(50, 20, 30), toBeCreated());

		assertEquals(50, created.getHostRateLimit());
		assertEquals(20, created.getShortRateLimit());
		assertEquals(30, created.getLongRateLimit());
	}

	@Test
	void aRateLimitOfZeroIsAllowedAndMeansNoJobCanRunOnTheHost() {
		when(programRepo.findById(1L)).thenReturn(Optional.of(new Program()));
		when(hostRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Host created = service.createHost(request(0, 0, 0), toBeCreated());

		assertEquals(0, created.getHostRateLimit());
	}

	@Test
	void aMissingRateLimitIsRefusedAndNothingIsSaved() {
		for (HostCreationRequest missing : new HostCreationRequest[] { request(null, 1, 1), request(5, null, 1),
				request(5, 1, null), request(null, null, null) })
			assertThrows(IllegalArgumentException.class, () -> service.createHost(missing, toBeCreated()));

		verify(hostRepo, never()).save(any());
	}

	@Test
	void aNegativeRateLimitIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> service.createHost(request(-1, 0, 0), toBeCreated()));
		assertThrows(IllegalArgumentException.class, () -> service.createHost(request(10, -1, 0), toBeCreated()));
		assertThrows(IllegalArgumentException.class, () -> service.createHost(request(10, 0, -1), toBeCreated()));
		verify(hostRepo, never()).save(any());
	}

	@Test
	void incompatibleRateLimitsAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> service.createHost(request(10, 8, 8), toBeCreated()));
		verify(hostRepo, never()).save(any());
	}

	@Test
	void anUnknownProgramIsNotFound() {
		when(programRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.createHost(request(10, 5, 5), toBeCreated()));
	}

}
