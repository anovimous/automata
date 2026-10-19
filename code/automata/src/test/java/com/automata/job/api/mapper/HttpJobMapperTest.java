package com.automata.job.api.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.automata.job.api.dto.HttpJobResponseDto;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;

class HttpJobMapperTest {

	@Test
	void fullDetailsAreMappedIncludingProgramAndRoutine() {
		HttpJobFullDetailsInternalDto dto = new HttpJobFullDetailsInternalDto(7L, HttpJobScope.WIDE, JobState.PAUSED,
				JobState.RUNNING, null, 3, 20, null, null, null, 11L, "prog", 12L, "routine-key", "it broke",
				com.automata.job.domain.model.enums.ConfigFileStatus.FAILED, "S3 is down");

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
	}

}
