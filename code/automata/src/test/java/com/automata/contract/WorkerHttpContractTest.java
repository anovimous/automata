package com.automata.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.automata.GlobalExceptionHandler;
import com.automata.job.api.HttpJobIntegrationController;
import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.service.HttpJobQueryService;
import com.automata.job.service.HttpJobStateService;

import jakarta.persistence.EntityNotFoundException;

/**
 * The HTTP answers of the endpoints which the orchestrator calls, as the
 * orchestrator sees them (see docs/worker-contract.md).
 */
@ExtendWith(MockitoExtension.class)
class WorkerHttpContractTest {

	@Mock
	HttpJobStateService stateService;

	@Mock
	HttpJobQueryService queryService;

	@InjectMocks
	HttpJobIntegrationController controller;

	MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	private String update(String newState) {
		return "{\"jobId\":5,\"newState\":\"" + newState + "\",\"message\":\"m\"}";
	}

	@Test
	void anAcceptedReportIsAnswered204() throws Exception {
		mvc.perform(post("/api/external/jobs/http/update/5").contentType(MediaType.APPLICATION_JSON)
				.content(update("RUNNING"))).andExpect(status().isNoContent());

		verify(stateService).updateCurrentJobState(5L, JobState.RUNNING, "m");
	}

	@Test
	void aReportOfAnUnknownJobIsAnswered404() throws Exception {
		doThrow(new EntityNotFoundException()).when(stateService).updateCurrentJobState(eq(5L), any(), any());

		mvc.perform(post("/api/external/jobs/http/update/5").contentType(MediaType.APPLICATION_JSON)
				.content(update("RUNNING"))).andExpect(status().isNotFound());
	}

	@Test
	void aReportOfAStateTheJobCanNotMoveToIsAnswered409() throws Exception {
		doThrow(new InvalidJobStateException("no")).when(stateService).updateCurrentJobState(eq(5L), any(), any());

		mvc.perform(post("/api/external/jobs/http/update/5").contentType(MediaType.APPLICATION_JSON)
				.content(update("FINISHED"))).andExpect(status().isConflict());
	}

	@Test
	void aPollOfAnUnknownJobIsAnswered404() throws Exception {
		doThrow(new EntityNotFoundException()).when(stateService).recordWorkerContact(5L);

		mvc.perform(get("/api/external/jobs/http/poll/5")).andExpect(status().isNotFound());
	}

}
