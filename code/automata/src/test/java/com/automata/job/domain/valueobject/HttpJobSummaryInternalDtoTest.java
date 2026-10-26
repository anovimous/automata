package com.automata.job.domain.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;
import com.automata.routine.Routine;

class HttpJobSummaryInternalDtoTest {

	@Test
	void ofMapsAiCreatedUnapprovedJob() {
		WideHttpJob job = new WideHttpJob();
		job.setId(1L);
		job.setCreationDate(Instant.EPOCH);
		job.setGenericDetails(HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.WIDE)
				.currentState(JobState.DRAFT).priority(2).rate(20).build());
		Routine routine = new Routine();
		routine.setId(5L);
		routine.setKey("fuzz");
		job.setRoutine(routine);
		job.setCreatedBy(JobCreator.AI);
		job.setApproved(false);

		HttpJobSummaryInternalDto dto = HttpJobSummaryInternalDto.of(job);

		assertEquals(JobCreator.AI, dto.createdBy());
		assertFalse(dto.approved());
		assertFalse(dto.terminal());
		assertTrue(dto.waitingForAction());
	}

	@Test
	void ofMapsUserCreatedApprovedJobByDefault() {
		WideHttpJob job = new WideHttpJob();
		job.setId(2L);
		job.setCreationDate(Instant.EPOCH);
		job.setGenericDetails(HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.WIDE)
				.currentState(JobState.RUNNING).priority(1).rate(10).build());
		Routine routine = new Routine();
		routine.setId(3L);
		routine.setKey("masser");
		job.setRoutine(routine);
		job.setCreatedBy(JobCreator.USER);
		job.setApproved(true);

		HttpJobSummaryInternalDto dto = HttpJobSummaryInternalDto.of(job);

		assertEquals(JobCreator.USER, dto.createdBy());
		assertTrue(dto.approved());
		assertFalse(dto.terminal());
		assertFalse(dto.waitingForAction());
	}

	@Test
	void aDraftWithFailedConfigFilesIsWaitingForAction() {
		WideHttpJob job = new WideHttpJob();
		job.setId(3L);
		job.setCreationDate(Instant.EPOCH);
		job.setGenericDetails(HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.WIDE)
				.currentState(JobState.DRAFT).priority(1).rate(10)
				.configFileStatus(com.automata.job.domain.model.enums.ConfigFileStatus.FAILED).build());
		Routine routine = new Routine();
		routine.setId(3L);
		routine.setKey("masser");
		job.setRoutine(routine);
		job.setCreatedBy(JobCreator.USER);
		job.setApproved(true);

		assertTrue(HttpJobSummaryInternalDto.of(job).waitingForAction());
	}

	@Test
	void aFinishedJobIsTerminal() {
		WideHttpJob job = new WideHttpJob();
		job.setId(4L);
		job.setCreationDate(Instant.EPOCH);
		job.setGenericDetails(HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.WIDE)
				.currentState(JobState.FINISHED).priority(1).rate(10).build());
		Routine routine = new Routine();
		routine.setId(3L);
		routine.setKey("masser");
		job.setRoutine(routine);
		job.setCreatedBy(JobCreator.USER);
		job.setApproved(true);

		HttpJobSummaryInternalDto dto = HttpJobSummaryInternalDto.of(job);

		assertTrue(dto.terminal());
		assertFalse(dto.waitingForAction());
	}

}
