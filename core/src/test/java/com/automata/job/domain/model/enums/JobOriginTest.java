package com.automata.job.domain.model.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class JobOriginTest {

	private static final UUID CONTEXT = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Test
	void jobOfTheAskingContextIsThisContext() {
		assertEquals(JobOrigin.THIS_CONTEXT, JobOrigin.of(JobCreator.AI, CONTEXT, CONTEXT));
	}

	@Test
	void aiJobOfAnotherContextIsOtherContext() {
		assertEquals(JobOrigin.OTHER_CONTEXT, JobOrigin.of(JobCreator.AI, UUID.randomUUID(), CONTEXT));
	}

	@Test
	void aiJobWithoutAContextIsOtherContext() {
		assertEquals(JobOrigin.OTHER_CONTEXT, JobOrigin.of(JobCreator.AI, null, CONTEXT));
	}

	@Test
	void jobOfAUserIsUser() {
		assertEquals(JobOrigin.USER, JobOrigin.of(JobCreator.USER, null, CONTEXT));
	}

}
