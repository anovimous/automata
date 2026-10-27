package com.automata.job.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.automata.job.api.dto.HttpJobFilter;
import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.exception.JobNotApprovedException;
import com.automata.job.domain.model.enums.ConfigFileStatus;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
import com.automata.job.domain.valueobject.HttpJobSummaryInternalDto;
import com.automata.job.service.HttpJobCreationService;
import com.automata.job.service.HttpJobQueryService;
import com.automata.job.service.HttpJobStateService;
import com.automata.testsupport.KnownBug;
import com.automata.testsupport.Mvc;

import jakarta.persistence.EntityNotFoundException;

/** The HTTP contract of the job routes: statuses, parameter binding and how service errors are shown. */
@ExtendWith(MockitoExtension.class)
class HttpJobControllerEndpointsTest {

	@Mock
	HttpJobCreationService creationService;

	@Mock
	HttpJobQueryService queryService;

	@Mock
	HttpJobStateService stateService;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = Mvc.standalone(new HttpJobController(creationService, queryService, stateService)).build();
	}

	private static HttpJobFullDetailsInternalDto fullDetails(JobState state, JobCreator createdBy, boolean approved) {
		return new HttpJobFullDetailsInternalDto(7L, HttpJobScope.NARROW, state, null, ResultsVerbosity.NORMAL, 3, 10, null, null,
				null, 2L, "Acme", 5L, "fuzz", null, null, ConfigFileStatus.READY, null, null, null, createdBy, approved, null);
	}

	// ---- read ----

	@Test
	void getJobShowsTheDerivedFlags() throws Exception {
		when(queryService.getJobFullDetails(7L)).thenReturn(fullDetails(JobState.FINISHED, JobCreator.USER, false));

		mvc.perform(get("/api/jobs/http/7")).andExpect(status().isOk()).andExpect(jsonPath("$.jobId").value(7))
				.andExpect(jsonPath("$.terminal").value(true)).andExpect(jsonPath("$.waitingForAction").value(false))
				.andExpect(jsonPath("$.program.name").value("Acme")).andExpect(jsonPath("$.routine.key").value("fuzz"));
	}

	@Test
	void anUnapprovedAiDraftIsWaitingForAction() throws Exception {
		when(queryService.getJobFullDetails(7L)).thenReturn(fullDetails(JobState.DRAFT, JobCreator.AI, false));

		mvc.perform(get("/api/jobs/http/7")).andExpect(jsonPath("$.waitingForAction").value(true))
				.andExpect(jsonPath("$.terminal").value(false));
	}

	@Test
	void getUnknownJobAnswers404() throws Exception {
		when(queryService.getJobFullDetails(9L)).thenThrow(new EntityNotFoundException("Job not found"));

		mvc.perform(get("/api/jobs/http/9")).andExpect(status().isNotFound());
	}

	@Test
	void listBindsTheFilterAndTheSummaryFlags() throws Exception {
		HttpJobSummaryInternalDto summary = new HttpJobSummaryInternalDto(1L, Instant.parse("2026-01-01T00:00:00Z"), HttpJobScope.WIDE,
				JobState.RUNNING, 2, 5, 3L, "fuzz", JobCreator.USER, false, false, false);
		when(queryService.getAllJobs(any(), any())).thenReturn(new PageImpl<>(List.of(summary)));

		mvc.perform(get("/api/jobs/http").param("programId", "2").param("scope", "WIDE").param("currentStates", "RUNNING,PAUSED")
				.param("createdBy", "AI").param("agentContextId", "4").param("targetRequestId", "8")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].routineKey").value("fuzz"))
				.andExpect(jsonPath("$.content[0].currentState").value("RUNNING"));

		ArgumentCaptor<HttpJobFilter> filter = ArgumentCaptor.forClass(HttpJobFilter.class);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(queryService).getAllJobs(filter.capture(), pageable.capture());
		assertEquals(2L, filter.getValue().programId());
		assertEquals(HttpJobScope.WIDE, filter.getValue().scope());
		assertEquals(Set.of(JobState.RUNNING, JobState.PAUSED), filter.getValue().currentStates());
		assertEquals(JobCreator.AI, filter.getValue().createdBy());
		assertEquals(4L, filter.getValue().agentContextId());
		assertEquals(8L, filter.getValue().targetRequestId());
		assertNull(filter.getValue().hostId());
		assertEquals(20, pageable.getValue().getPageSize());
		assertEquals("creationDate: DESC", pageable.getValue().getSort().toString());
	}

	@Test
	@KnownBug("BUG-004")
	void listWithAnUnknownStateAnswers400() throws Exception {
		mvc.perform(get("/api/jobs/http").param("currentStates", "SLEEPING")).andExpect(status().isBadRequest());
	}

	// ---- state changes ----

	@Test
	void approveAnswers204() throws Exception {
		mvc.perform(post("/api/jobs/http/7/approve")).andExpect(status().isNoContent());

		verify(stateService).approveJob(7L);
	}

	@Test
	void approveOfAJobThatIsNotAnAiDraftAnswers409() throws Exception {
		doThrow(new InvalidJobStateException("Only AI-created jobs can be approved")).when(stateService).approveJob(7L);

		mvc.perform(post("/api/jobs/http/7/approve")).andExpect(status().isConflict());
	}

	@Test
	void aiResultAnswers204AndPassesTheText() throws Exception {
		mvc.perform(post("/api/jobs/http/7/ai-result").contentType(MediaType.APPLICATION_JSON).content("{\"aiResult\":\"found nothing\"}"))
				.andExpect(status().isNoContent());

		verify(stateService).setAiResult(7L, "found nothing");
	}

	@Test
	void aiResultThatIsTooLongAnswers400() throws Exception {
		doThrow(new IllegalArgumentException("aiResult is too long")).when(stateService).setAiResult(any(), any());

		mvc.perform(post("/api/jobs/http/7/ai-result").contentType(MediaType.APPLICATION_JSON).content("{\"aiResult\":\"x\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void queueAnswers204() throws Exception {
		mvc.perform(post("/api/jobs/http/7/queue")).andExpect(status().isNoContent());

		verify(stateService).prepareJobForQueueing(7L);
	}

	@Test
	void queueOfAnUnapprovedAiJobAnswers409() throws Exception {
		doThrow(new JobNotApprovedException(7L)).when(stateService).prepareJobForQueueing(7L);

		mvc.perform(post("/api/jobs/http/7/queue")).andExpect(status().isConflict());
	}

	@Test
	void queueOfAJobThatIsNotADraftAnswers409() throws Exception {
		doThrow(InvalidJobStateException.requiredState(JobState.DRAFT, JobState.RUNNING, "prepare it for queueing"))
				.when(stateService).prepareJobForQueueing(7L);

		mvc.perform(post("/api/jobs/http/7/queue")).andExpect(status().isConflict());
	}

	@Test
	void retryPreparationAnswers202() throws Exception {
		mvc.perform(post("/api/jobs/http/7/retry-preparation")).andExpect(status().isAccepted());

		verify(stateService).retryConfigFilePreparation(7L);
	}

	@Test
	void pauseAndResumeAreOnlyRequestsSoTheyAnswer202() throws Exception {
		mvc.perform(post("/api/jobs/http/7/pause")).andExpect(status().isAccepted());
		mvc.perform(post("/api/jobs/http/7/resume")).andExpect(status().isAccepted());

		verify(stateService).pauseRunningJob(7L);
		verify(stateService).resumePausedJob(7L);
	}

	@Test
	void pauseOfAJobThatIsNotRunningAnswers409() throws Exception {
		doThrow(InvalidJobStateException.requiredState(JobState.RUNNING, JobState.QUEUED, "pause it")).when(stateService)
				.pauseRunningJob(7L);

		mvc.perform(post("/api/jobs/http/7/pause")).andExpect(status().isConflict());
	}

	@Test
	void cancelAnswers204() throws Exception {
		mvc.perform(post("/api/jobs/http/7/cancel")).andExpect(status().isNoContent());

		verify(stateService).cancelJob(7L);
	}

	@Test
	void cancelOfAScheduledJobAnswers501() throws Exception {
		doThrow(new UnsupportedOperationException("Canceling scheduled jobs is not implemented yet")).when(stateService).cancelJob(7L);

		mvc.perform(post("/api/jobs/http/7/cancel")).andExpect(status().isNotImplemented());
	}

	@Test
	void stateChangeOfAnUnknownJobAnswers404() throws Exception {
		doThrow(new EntityNotFoundException("HttpJob with the given id has not been found")).when(stateService).cancelJob(9L);

		mvc.perform(post("/api/jobs/http/9/cancel")).andExpect(status().isNotFound());
	}

	@Test
	void deleteIsNotImplementedAndAnswers501() throws Exception {
		doThrow(new UnsupportedOperationException("Delete operation is not implemented yet")).when(stateService).deleteJob(7L);

		mvc.perform(delete("/api/jobs/http/7")).andExpect(status().isNotImplemented());
	}

	// ---- creation ----

	@Test
	@KnownBug("BUG-004")
	void creatingAJobWithoutAScopeAnswers400() throws Exception {
		mvc.perform(post("/api/jobs/http").contentType(MediaType.APPLICATION_JSON).content("{\"genericDetails\":{}}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aPageOfNoJobsIsAnEmptyContent() throws Exception {
		Page<HttpJobSummaryInternalDto> empty = new PageImpl<>(List.of());
		when(queryService.getAllJobs(any(), any())).thenReturn(empty);

		mvc.perform(get("/api/jobs/http")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
		assertTrue(empty.isEmpty());
	}
}
