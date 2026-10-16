package com.automata.job.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.valueobject.HttpJobInternalDto;

class JobDispatchOrderTest {

	private static HttpJobInternalDto job(long id, long programId, int priority, long createdAtSecond) {
		return new HttpJobInternalDto(id, Instant.ofEpochSecond(createdAtSecond), HttpJobScope.WIDE, null, priority,
				10, programId, null);
	}

	private static List<Long> sortedIds(List<HttpJobInternalDto> jobs) {
		return JobDispatchOrder.sort(jobs).stream().map(HttpJobInternalDto::id).toList();
	}

	private static List<Long> sortedIds(HttpJobInternalDto... jobs) {
		return sortedIds(List.of(jobs));
	}

	@Test
	void isEagerFromPriorityFour() {
		assertFalse(JobDispatchOrder.isEager(3));
		assertTrue(JobDispatchOrder.isEager(4));
		assertTrue(JobDispatchOrder.isEager(5));
	}

	@Test
	void emptyInputGivesEmptyOrder() {
		assertEquals(List.of(), sortedIds(List.of()));
	}

	@Test
	void sameProgramIsOrderedByHigherPriorityFirst() {
		assertEquals(List.of(2L, 3L, 1L), sortedIds(job(1, 1, 1, 0), job(2, 1, 3, 9), job(3, 1, 2, 5)));
	}

	@Test
	void sameProgramAndPriorityIsOrderedByCreationDateThenId() {
		assertEquals(List.of(2L, 1L, 3L), sortedIds(job(1, 1, 2, 5), job(2, 1, 2, 0), job(3, 1, 2, 5)));
	}

	@Test
	void differentProgramsAreOrderedByCreationDateWhateverTheirPriority() {
		assertEquals(List.of(2L, 1L), sortedIds(job(1, 1, 3, 9), job(2, 2, 1, 0)));
	}

	@Test
	void differentProgramsCreatedAtTheSameTimeAreBothKeptInOrderOfId() {
		assertEquals(List.of(1L, 2L), sortedIds(job(2, 2, 1, 5), job(1, 1, 1, 5)));
	}

	@Test
	void programsAreInterleavedByTheAgeOfTheirNextJob() {
		// program 1 holds X (age 1) then Y (age 10); program 2 holds Z (age 5)
		HttpJobInternalDto x = job(1, 1, 3, 1);
		HttpJobInternalDto y = job(2, 1, 2, 10);
		HttpJobInternalDto z = job(3, 2, 1, 5);

		assertEquals(List.of(1L, 3L, 2L), sortedIds(y, z, x));
	}

	@Test
	void eagerJobsComeBeforeLazyJobsOfAnyProgramHigherPriorityFirst() {
		HttpJobInternalDto oldLazy = job(1, 1, 3, 0);
		HttpJobInternalDto newEager = job(2, 2, 4, 9);
		HttpJobInternalDto newRetriedEager = job(3, 3, 5, 9);

		assertEquals(List.of(3L, 2L, 1L), sortedIds(oldLazy, newEager, newRetriedEager));
	}

	@Test
	void eagerJobsOfTheSamePriorityAreOrderedByAgeAcrossPrograms() {
		assertEquals(List.of(2L, 1L), sortedIds(job(1, 1, 4, 9), job(2, 2, 4, 0)));
	}

	@Test
	void eagerJobOfAnotherProgramPassesTheOlderLazyJobsOfAProgram() {
		assertEquals(List.of(3L, 1L, 2L), sortedIds(job(1, 1, 3, 0), job(2, 1, 2, 1), job(3, 2, 4, 9)));
	}

	@Test
	void orderIsTheSameWhateverTheInsertionOrderEvenWhenPriorityAndAgeContradictEachOther() {
		// priority orders A before C (same program) while age orders C before B and B
		// before A: as pairs these are a cycle, as a sequence it is C, B, A
		HttpJobInternalDto a = job(1, 1, 1, 3);
		HttpJobInternalDto c = job(2, 1, 2, 1);
		HttpJobInternalDto b = job(3, 2, 1, 2);

		List<List<HttpJobInternalDto>> insertionOrders = List.of(List.of(a, b, c), List.of(a, c, b),
				List.of(b, a, c), List.of(b, c, a), List.of(c, a, b), List.of(c, b, a));

		for (List<HttpJobInternalDto> insertionOrder : insertionOrders)
			assertEquals(List.of(2L, 3L, 1L), sortedIds(insertionOrder));
	}

	@Test
	void everyJobIsKeptExactlyOnce() {
		List<HttpJobInternalDto> jobs = new ArrayList<>();
		for (long id = 1; id <= 30; id++)
			jobs.add(job(id, id % 4, (int) (id % 5) + 1, id % 3));

		List<HttpJobInternalDto> sorted = JobDispatchOrder.sort(jobs);

		assertEquals(30, sorted.size());
		assertEquals(30, sorted.stream().map(HttpJobInternalDto::id).distinct().count());
	}

	@Test
	void givenCollectionIsNotModified() {
		List<HttpJobInternalDto> jobs = new ArrayList<>(List.of(job(2, 1, 1, 0), job(1, 1, 3, 0)));

		JobDispatchOrder.sort(jobs);

		assertEquals(List.of(2L, 1L), jobs.stream().map(HttpJobInternalDto::id).toList());
	}

}
