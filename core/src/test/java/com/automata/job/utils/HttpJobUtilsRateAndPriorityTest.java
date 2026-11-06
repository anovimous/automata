package com.automata.job.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.automata.job.domain.model.HttpJob;

class HttpJobUtilsRateAndPriorityTest {

	@Test
	void aMissingRateIsNotChecked() {
		HttpJobUtils.requireValidRate(null, "rate");
	}

	@Test
	void aRateFromOneUpIsValid() {
		HttpJobUtils.requireValidRate(HttpJob.MIN_RATE, "rate");
		HttpJobUtils.requireValidRate(1000, "rate");
	}

	@Test
	void aRateOfZeroOrLessIsRefusedWithTheNameOfTheField() {
		IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
				() -> HttpJobUtils.requireValidRate(0, "maxRate"));
		assertEquals("maxRate must be at least 1", zero.getMessage());
		assertThrows(IllegalArgumentException.class, () -> HttpJobUtils.requireValidRate(-1, "rate"));
	}

	@Test
	void aMissingPriorityIsNotChecked() {
		HttpJobUtils.requireValidPriority(null, "priority");
	}

	@Test
	void aPriorityInTheRangeIsValid() {
		HttpJobUtils.requireValidPriority(HttpJob.MIN_PRIORITY, "priority");
		HttpJobUtils.requireValidPriority(HttpJob.MAX_PRIORITY, "priority");
	}

	@Test
	void aPriorityOutsideTheRangeIsRefusedWithTheNameOfTheField() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> HttpJobUtils.requireValidPriority(6, "defaultPriority"));
		assertEquals("defaultPriority must be between 1 and 5", e.getMessage());
		assertThrows(IllegalArgumentException.class, () -> HttpJobUtils.requireValidPriority(0, "priority"));
	}

}
