package com.automata.agentcontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.automata.GlobalExceptionHandler;
import com.automata.job.api.dto.AiJobCreationRequest;
import com.automata.job.api.dto.HttpJobFilter;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.service.HttpJobCreationService;
import com.automata.job.service.HttpJobQueryService;
import com.automata.job.service.HttpJobStateService;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class AgentContextJobControllerTest {

	private static final UUID CONTEXT_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static final String BASE = "/api/agent-contexts/uuid/" + CONTEXT_UUID + "/jobs";

	@Mock
	private AgentContextService contextService;
	@Mock
	private HttpJobCreationService creationService;
	@Mock
	private HttpJobQueryService queryService;
	@Mock
	private HttpJobStateService stateService;

	@InjectMocks
	private AgentContextJobController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
	}

	private void jobNotOfContext(Long jobId) {
		when(queryService.getJobOfContext(CONTEXT_UUID, jobId)).thenThrow(new EntityNotFoundException("Job not found"));
	}

	// list

	@Test
	void getJobs_alwaysFiltersOnTheAiJobsOfTheContext() throws Exception {
		when(contextService.getContextByUuid(CONTEXT_UUID)).thenReturn(AgentContext.builder().id(9L).build());
		when(queryService.getAllJobs(any(), any())).thenReturn(new PageImpl<>(java.util.List.of()));

		mvc.perform(get(BASE).param("states", "FINISHED,FAILED")).andExpect(status().isOk());

		ArgumentCaptor<HttpJobFilter> filter = ArgumentCaptor.forClass(HttpJobFilter.class);
		verify(queryService).getAllJobs(filter.capture(), any());
		assertEquals(JobCreator.AI, filter.getValue().createdBy());
		assertEquals(9L, filter.getValue().agentContextId());
		assertEquals(Set.of(JobState.FINISHED, JobState.FAILED), filter.getValue().currentStates());
		assertNull(filter.getValue().hostId());
		assertNull(filter.getValue().programId());
	}

	@Test
	void getJobs_withoutStates_hasNoStateFilter() throws Exception {
		when(contextService.getContextByUuid(CONTEXT_UUID)).thenReturn(AgentContext.builder().id(9L).build());
		when(queryService.getAllJobs(any(), any())).thenReturn(new PageImpl<>(java.util.List.of()));

		mvc.perform(get(BASE)).andExpect(status().isOk());

		ArgumentCaptor<HttpJobFilter> filter = ArgumentCaptor.forClass(HttpJobFilter.class);
		verify(queryService).getAllJobs(filter.capture(), any());
		assertNull(filter.getValue().currentStates());
	}

	@Test
	void getJobs_unknownContext_is404() throws Exception {
		when(contextService.getContextByUuid(CONTEXT_UUID))
				.thenThrow(new EntityNotFoundException("AgentContext not found"));

		mvc.perform(get(BASE)).andExpect(status().isNotFound());

		verifyNoInteractions(queryService);
	}

	// get one

	@Test
	void getJob_notOfTheContext_is404_andReadsNoDetails() throws Exception {
		jobNotOfContext(7L);

		mvc.perform(get(BASE + "/7")).andExpect(status().isNotFound());

		verify(queryService, never()).getJobFullDetails(any());
	}

	@Test
	void getJobResult_notOfTheContext_is404_andReadsNoResult() throws Exception {
		jobNotOfContext(7L);

		mvc.perform(get(BASE + "/7/result")).andExpect(status().isNotFound());

		verify(queryService, never()).getJobResult(any());
	}

	// create

	@Test
	void createJob_usesTheContextOfThePath() throws Exception {
		NarrowHttpJob created = new NarrowHttpJob();
		created.setId(31L);
		when(creationService.createDraftAiNarrowJob(eq(CONTEXT_UUID), any())).thenReturn(created);

		mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
				.content("{\"requestId\":5,\"aiDescription\":\"goal\",\"routineId\":1,\"wordlistsIds\":[]}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/agent-contexts/uuid/" + CONTEXT_UUID + "/jobs/31"));

		ArgumentCaptor<AiJobCreationRequest> request = ArgumentCaptor.forClass(AiJobCreationRequest.class);
		verify(creationService).createDraftAiNarrowJob(eq(CONTEXT_UUID), request.capture());
		assertEquals(5L, request.getValue().requestId());
		assertEquals("goal", request.getValue().aiDescription());
		assertTrue(request.getValue().wordlistsIds().isEmpty());
	}

	@Test
	void createJob_requestNotInTheContext_is404() throws Exception {
		when(creationService.createDraftAiNarrowJob(eq(CONTEXT_UUID), any()))
				.thenThrow(new EntityNotFoundException("Request not found in the agent context"));

		mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
				.content("{\"requestId\":5,\"aiDescription\":\"goal\",\"routineId\":1,\"wordlistsIds\":[]}"))
				.andExpect(status().isNotFound());
	}

	// state changes

	@Test
	void queueJob_ofTheContext_queuesIt() throws Exception {
		mvc.perform(post(BASE + "/7/queue")).andExpect(status().isNoContent());

		verify(stateService).prepareJobForQueueing(7L);
	}

	@Test
	void queueJob_notOfTheContext_is404_andQueuesNothing() throws Exception {
		jobNotOfContext(7L);

		mvc.perform(post(BASE + "/7/queue")).andExpect(status().isNotFound());

		verifyNoInteractions(stateService);
	}

	@Test
	void retryPreparation_ofTheContext_isAccepted() throws Exception {
		mvc.perform(post(BASE + "/7/retry-preparation")).andExpect(status().isAccepted());

		verify(stateService).retryConfigFilePreparation(7L);
	}

	@Test
	void retryPreparation_notOfTheContext_is404() throws Exception {
		jobNotOfContext(7L);

		mvc.perform(post(BASE + "/7/retry-preparation")).andExpect(status().isNotFound());

		verifyNoInteractions(stateService);
	}

	@Test
	void setAiResult_ofTheContext_setsIt() throws Exception {
		mvc.perform(post(BASE + "/7/ai-result").contentType(MediaType.APPLICATION_JSON)
				.content("{\"aiResult\":\"nothing found\"}")).andExpect(status().isNoContent());

		verify(stateService).setAiResult(7L, "nothing found");
	}

	@Test
	void setAiResult_notOfTheContext_is404() throws Exception {
		jobNotOfContext(7L);

		mvc.perform(post(BASE + "/7/ai-result").contentType(MediaType.APPLICATION_JSON)
				.content("{\"aiResult\":\"x\"}")).andExpect(status().isNotFound());

		verifyNoInteractions(stateService);
	}

	@Test
	void cancelJob_ofTheContext_cancelsIt() throws Exception {
		mvc.perform(post(BASE + "/7/cancel")).andExpect(status().isNoContent());

		verify(stateService).cancelJob(7L);
	}

	@Test
	void cancelJob_notOfTheContext_is404_andCancelsNothing() throws Exception {
		jobNotOfContext(7L);

		mvc.perform(post(BASE + "/7/cancel")).andExpect(status().isNotFound());

		verifyNoInteractions(stateService);
	}

	@Test
	void cancelJob_stateRulesOfTheCoreApply() throws Exception {
		doThrow(new com.automata.job.domain.exception.InvalidJobStateException("cannot cancel")).when(stateService)
				.cancelJob(7L);

		mvc.perform(post(BASE + "/7/cancel")).andExpect(status().is4xxClientError());
	}

}
