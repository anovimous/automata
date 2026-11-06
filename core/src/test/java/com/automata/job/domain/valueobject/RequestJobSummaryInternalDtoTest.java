package com.automata.job.domain.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.automata.agentcontext.AgentContext;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobOrigin;
import com.automata.job.domain.model.enums.JobState;
import com.automata.routine.Routine;

class RequestJobSummaryInternalDtoTest {

	private static final UUID CONTEXT = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private HttpJob job(JobCreator createdBy, UUID jobContext) {
		HttpJob job = new WideHttpJob();
		job.setId(5L);
		job.setCreationDate(Instant.EPOCH);
		job.setCreatedBy(createdBy);
		job.setApproved(true);
		job.setGenericDetails(HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.NARROW)
				.currentState(JobState.FINISHED).priority(2).rate(30).build());
		Routine routine = new Routine();
		routine.setKey("fuzz");
		job.setRoutine(routine);
		if (jobContext != null)
			job.setAgentContext(AgentContext.builder().uuid(jobContext).build());
		return job;
	}

	@Test
	void aFinishedJobIsTerminalAndNotWaiting() {
		RequestJobSummaryInternalDto dto = RequestJobSummaryInternalDto.of(job(JobCreator.AI, CONTEXT), CONTEXT);

		org.junit.jupiter.api.Assertions.assertTrue(dto.terminal());
		org.junit.jupiter.api.Assertions.assertFalse(dto.waitingForAction());
	}

	@Test
	void anUnapprovedAiDraftIsWaitingForAction() {
		HttpJob draft = job(JobCreator.AI, CONTEXT);
		draft.setApproved(false);
		draft.getGenericDetails().setCurrentState(JobState.DRAFT);

		RequestJobSummaryInternalDto dto = RequestJobSummaryInternalDto.of(draft, CONTEXT);

		org.junit.jupiter.api.Assertions.assertFalse(dto.terminal());
		org.junit.jupiter.api.Assertions.assertTrue(dto.waitingForAction());
	}

	@Test
	void fieldsAreCopiedFromTheJob() {
		RequestJobSummaryInternalDto dto = RequestJobSummaryInternalDto.of(job(JobCreator.AI, CONTEXT), CONTEXT);

		assertEquals(5L, dto.id());
		assertEquals(Instant.EPOCH, dto.creationDate());
		assertEquals(HttpJobScope.NARROW, dto.scope());
		assertEquals(JobState.FINISHED, dto.currentState());
		assertEquals(2, dto.priority());
		assertEquals(30, dto.rate());
		assertEquals("fuzz", dto.routineKey());
		assertEquals(true, dto.approved());
	}

	@Test
	void jobOfTheAskingContextIsThisContext() {
		assertEquals(JobOrigin.THIS_CONTEXT,
				RequestJobSummaryInternalDto.of(job(JobCreator.AI, CONTEXT), CONTEXT).origin());
	}

	@Test
	void jobOfAnotherContextIsOtherContext() {
		assertEquals(JobOrigin.OTHER_CONTEXT,
				RequestJobSummaryInternalDto.of(job(JobCreator.AI, UUID.randomUUID()), CONTEXT).origin());
	}

	@Test
	void jobOfAUserWithoutAContextIsUser() {
		assertEquals(JobOrigin.USER, RequestJobSummaryInternalDto.of(job(JobCreator.USER, null), CONTEXT).origin());
	}

}
