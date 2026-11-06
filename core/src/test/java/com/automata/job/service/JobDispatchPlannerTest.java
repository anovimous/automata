package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.host.HostRepository;
import com.automata.host.common.dto.HostAllRateLimitsInternalDto;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.valueobject.HttpJobInternalDto;
import com.automata.job.domain.valueobject.ProgramSummaryRatesDto;
import com.automata.job.repository.NarrowHttpJobRepository;
import com.automata.routine.common.enums.Duration;

@ExtendWith(MockitoExtension.class)
class JobDispatchPlannerTest {

	private static final Long PROGRAM = 1L;

	@Mock
	NarrowHttpJobRepository narrowJobRepo;
	@Mock
	HostRepository hostRepo;

	@InjectMocks
	JobDispatchPlanner planner;

	private static HttpJobInternalDto narrow(long id, int rate, long hostId) {
		return new HttpJobInternalDto(id, Instant.EPOCH, HttpJobScope.NARROW, Duration.SHORT, 1, rate, PROGRAM,
				hostId);
	}

	private static Map<Long, ProgramSummaryRatesDto> rates(int rateLimit) {
		return Map.of(PROGRAM,
				ProgramSummaryRatesDto.builder().rateLimit(rateLimit).currentRate(0L).currentWideRate(0L).build());
	}

	@Test
	void noCandidatesGiveNoAcceptedJobs() {
		assertTrue(planner.plan(List.of(), rates(100)).isEmpty());
	}

	@Test
	void acceptedJobsAreReturnedInTheOrderTheyWereAccepted() {
		HttpJobInternalDto first = narrow(1, 40, 10);
		HttpJobInternalDto second = narrow(2, 40, 11);
		when(hostRepo.findRateLimitsAllDtoById(10L)).thenReturn(new HostAllRateLimitsInternalDto(10L, 1000, 1000, 1000));
		when(hostRepo.findRateLimitsAllDtoById(11L)).thenReturn(new HostAllRateLimitsInternalDto(11L, 1000, 1000, 1000));

		assertEquals(List.of(first, second), planner.plan(List.of(first, second), rates(100)));
	}

	@Test
	void jobOverTheProgramLimitIsNotAccepted() {
		HttpJobInternalDto tooBig = narrow(1, 150, 10);

		assertTrue(planner.plan(List.of(tooBig), rates(100)).isEmpty());
	}

	@Test
	void jobAcceptedEarlierCountsAgainstTheLaterOnes() {
		HttpJobInternalDto first = narrow(1, 60, 10);
		HttpJobInternalDto second = narrow(2, 60, 11);
		when(hostRepo.findRateLimitsAllDtoById(10L)).thenReturn(new HostAllRateLimitsInternalDto(10L, 1000, 1000, 1000));

		assertEquals(List.of(first), planner.plan(List.of(first, second), rates(100)));
	}

	@Test
	void theGivenInputsAreNotModified() {
		HttpJobInternalDto tooBig = narrow(1, 150, 10);
		List<HttpJobInternalDto> candidates = new java.util.ArrayList<>(List.of(tooBig));
		Map<Long, ProgramSummaryRatesDto> programRates = rates(100);

		planner.plan(candidates, programRates);

		assertEquals(List.of(tooBig), candidates);
		assertEquals(100, programRates.get(PROGRAM).rateLimit());
	}

	private static HttpJobInternalDto wide(long id, int rate) {
		return new HttpJobInternalDto(id, Instant.EPOCH, HttpJobScope.WIDE, null, 1, rate, PROGRAM, null);
	}

	private static Map<Long, ProgramSummaryRatesDto> busyRates(int rateLimit, long currentRate) {
		return Map.of(PROGRAM, ProgramSummaryRatesDto.builder().rateLimit(rateLimit).currentRate(currentRate)
				.currentWideRate(0L).build());
	}

	@Test
	void aNarrowJobAboveTheProgramLimitOnItsOwnDoesNotHoldBackTheOthersOfItsHost() {
		HttpJobInternalDto oversized = narrow(1, 150, 10);
		HttpJobInternalDto small = narrow(2, 5, 10);
		when(hostRepo.findRateLimitsAllDtoById(10L)).thenReturn(new HostAllRateLimitsInternalDto(10L, 1000, 1000, 1000));

		assertEquals(List.of(small), planner.plan(List.of(oversized, small), rates(100)));
	}

	@Test
	void aNarrowJobWhichDoesNotFitBecauseTheProgramIsBusyStillHoldsBackTheOthersOfItsHostAndDuration() {
		HttpJobInternalDto waiting = narrow(1, 30, 10);
		HttpJobInternalDto behindIt = narrow(2, 5, 10);

		assertTrue(planner.plan(List.of(waiting, behindIt), busyRates(100, 80)).isEmpty());
	}

	@Test
	void aNarrowJobWhichDoesNotFitBecauseTheProgramIsBusyDoesNotHoldBackAnotherHost() {
		HttpJobInternalDto waiting = narrow(1, 30, 10);
		HttpJobInternalDto otherHost = narrow(2, 5, 11);
		when(hostRepo.findRateLimitsAllDtoById(11L)).thenReturn(new HostAllRateLimitsInternalDto(11L, 1000, 1000, 1000));

		assertEquals(List.of(otherHost), planner.plan(List.of(waiting, otherHost), busyRates(100, 80)));
	}

	@Test
	void aWideJobAboveTheProgramLimitOnItsOwnDoesNotHoldBackTheOtherWideJobs() {
		HttpJobInternalDto oversized = wide(1, 150);
		HttpJobInternalDto small = wide(2, 5);

		assertEquals(List.of(small), planner.plan(List.of(oversized, small), rates(100)));
	}

	@Test
	void aWideJobWhichDoesNotFitBecauseTheProgramIsBusyStillHoldsBackTheOtherWideJobs() {
		HttpJobInternalDto waiting = wide(1, 5);
		HttpJobInternalDto behindIt = wide(2, 1);

		assertTrue(planner.plan(List.of(waiting, behindIt), busyRates(100, 98)).isEmpty());
	}
}
