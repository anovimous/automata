package com.automata.job.api.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.automata.job.api.dto.HttpJobResponseDto;
import com.automata.job.api.dto.HttpJobSummaryResponseDto;
import com.automata.job.api.dto.RequestJobResponseDto;
import com.automata.job.api.dto.RequestJobSummaryResponseDto;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobOrigin;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
import com.automata.job.domain.valueobject.HttpJobSummaryInternalDto;
import com.automata.job.domain.valueobject.RequestJobSummaryInternalDto;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class HttpJobMapperTest {

	@Test
	void requestJobSummaryKeepsTheOrigin() {
		RequestJobSummaryResponseDto dto = HttpJobMapper.toResponseDto(new RequestJobSummaryInternalDto(4L,
				Instant.EPOCH, HttpJobScope.NARROW, JobState.FINISHED, 2, 30, "fuzz", true, JobOrigin.OTHER_CONTEXT));

		assertEquals(4L, dto.id());
		assertEquals("fuzz", dto.routineKey());
		assertEquals(JobOrigin.OTHER_CONTEXT, dto.origin());
		assertTrue(dto.approved());
	}

	@Test
	void requestJobReplacesTheContextUuidByItsOriginAndKeepsTheCustomConfig() {
		java.util.UUID asking = java.util.UUID.randomUUID();
		java.util.UUID owner = java.util.UUID.randomUUID();
		var config = JsonNodeFactory.instance.objectNode().put("flags", "-x");
		HttpJobFullDetailsInternalDto full = new HttpJobFullDetailsInternalDto(4L, HttpJobScope.NARROW,
				JobState.FAILED, null, null, 2, 30, null, null, config, 1L, "program", 7L, "fuzz", "boom", null,
				null, "desc", "result", JobCreator.AI, true, owner);

		RequestJobResponseDto dto = HttpJobMapper.toRequestJobResponseDto(full, asking);

		assertEquals(4L, dto.jobId());
		assertEquals(JobOrigin.OTHER_CONTEXT, dto.origin());
		assertEquals(config, dto.customConfig());
		assertEquals("fuzz", dto.routineKey());
		assertEquals("boom", dto.failureMessage());
		assertEquals("desc", dto.aiDescription());
		assertEquals("result", dto.aiResult());
		assertFalse(dto.toString().contains(owner.toString()));
		assertEquals(JobOrigin.THIS_CONTEXT, HttpJobMapper.toRequestJobResponseDto(full, owner).origin());
	}

	private static final java.util.UUID CONTEXT_UUID = java.util.UUID.randomUUID();

	@Test
	void fullDetailsAreMappedIncludingProgramAndRoutine() {
		HttpJobFullDetailsInternalDto dto = new HttpJobFullDetailsInternalDto(7L, HttpJobScope.WIDE, JobState.PAUSED,
				JobState.RUNNING, null, 3, 20, null, null, null, 11L, "prog", 12L, "routine-key", "it broke",
				com.automata.job.domain.model.enums.ConfigFileStatus.FAILED, "S3 is down", "test description",
				"test result", JobCreator.AI, false, CONTEXT_UUID);

		HttpJobResponseDto response = HttpJobMapper.toResponseDto(dto);

		assertEquals(7L, response.jobId());
		assertEquals(HttpJobScope.WIDE, response.httpJobScope());
		assertEquals(JobState.PAUSED, response.currentState());
		assertEquals(JobState.RUNNING, response.requestedState());
		assertEquals(3, response.priority());
		assertEquals(20, response.rate());
		assertEquals(11L, response.program().id());
		assertEquals("prog", response.program().name());
		assertEquals(12L, response.routine().id());
		assertEquals("routine-key", response.routine().key());
		assertEquals("it broke", response.failureMessage());
		assertEquals(com.automata.job.domain.model.enums.ConfigFileStatus.FAILED, response.configFileStatus());
		assertEquals("S3 is down", response.configFileFailure());
		assertEquals("test description", response.aiDescription());
		assertEquals("test result", response.aiResult());
		assertEquals(JobCreator.AI, response.createdBy());
		assertEquals(false, response.approved());
		assertEquals(CONTEXT_UUID, response.agentContextUuid());
	}

	@Test
	void fullDetailsWithNullAiFieldsAreMapped() {
		HttpJobFullDetailsInternalDto dto = new HttpJobFullDetailsInternalDto(1L, HttpJobScope.NARROW,
				JobState.DRAFT, null, null, 1, 10, null, null, null, 1L, "p", 1L, "r", null, null, null, null,
				null, JobCreator.USER, true, null);

		HttpJobResponseDto response = HttpJobMapper.toResponseDto(dto);

		assertNull(response.aiDescription());
		assertNull(response.aiResult());
		assertEquals(JobCreator.USER, response.createdBy());
		assertTrue(response.approved());
		assertNull(response.agentContextUuid());
	}

	@Test
	void summaryDtoMapsCreatedByAndApproved() {
		HttpJobSummaryInternalDto dto = new HttpJobSummaryInternalDto(1L, Instant.EPOCH, HttpJobScope.NARROW,
				JobState.RUNNING, 2, 30, 5L, "fuzz", JobCreator.AI, false);

		HttpJobSummaryResponseDto response = HttpJobMapper.toResponseDto(dto);

		assertEquals(JobCreator.AI, response.createdBy());
		assertFalse(response.approved());
	}

	@Test
	void summaryDtoMapsUserCreatedApproved() {
		HttpJobSummaryInternalDto dto = new HttpJobSummaryInternalDto(2L, Instant.EPOCH, HttpJobScope.WIDE,
				JobState.DRAFT, 1, 10, 3L, "masser", JobCreator.USER, true);

		HttpJobSummaryResponseDto response = HttpJobMapper.toResponseDto(dto);

		assertEquals(JobCreator.USER, response.createdBy());
		assertTrue(response.approved());
	}

}
