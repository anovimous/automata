package com.automata.job.domain.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProgramSummaryRatesDtoTest {

	private static final ProgramSummaryRatesDto RATES = new ProgramSummaryRatesDto(100, 30L, 10L);

	@Test
	void acceptedNarrowJobOnlyAddsToTotalRate() {
		ProgramSummaryRatesDto updated = RATES.withAcceptedJob(20, false);

		assertEquals(50L, updated.currentRate());
		assertEquals(10L, updated.currentWideRate());
		assertEquals(100, updated.rateLimit());
	}

	@Test
	void acceptedWideJobAddsToTotalAndWideRate() {
		ProgramSummaryRatesDto updated = RATES.withAcceptedJob(20, true);

		assertEquals(50L, updated.currentRate());
		assertEquals(30L, updated.currentWideRate());
		assertEquals(100, updated.rateLimit());
	}

	@Test
	void originalIsNotModified() {
		RATES.withAcceptedJob(20, true);

		assertEquals(30L, RATES.currentRate());
		assertEquals(10L, RATES.currentWideRate());
	}

}
