package com.automata.job.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.automata.job.api.dto.JobStateUpdateRequest;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.service.HttpJobQueryService;
import com.automata.job.service.HttpJobStateService;

@ExtendWith(MockitoExtension.class)
class HttpJobIntegrationControllerTest {

	@Mock
	HttpJobStateService stateService;
	@Mock
	HttpJobQueryService queryService;

	@InjectMocks
	HttpJobIntegrationController controller;

	@Test
	void jobToUpdateIsTheOneInThePathNotTheOneInTheBody() {
		ResponseEntity<Void> response = controller.updateJob(5L, new JobStateUpdateRequest(99L, JobState.RUNNING, "msg"));

		assertEquals(204, response.getStatusCode().value());
		verify(stateService).updateCurrentJobState(5L, JobState.RUNNING, "msg");
	}

}
