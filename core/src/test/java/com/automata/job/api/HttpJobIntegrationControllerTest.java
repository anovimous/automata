package com.automata.job.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.automata.job.api.dto.JobStateUpdateRequest;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.service.HttpJobQueryService;
import com.automata.job.service.HttpJobStateService;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class HttpJobIntegrationControllerTest {

	@Mock
	HttpJobStateService stateService;
	@Mock
	HttpJobQueryService queryService;

	@InjectMocks
	HttpJobIntegrationController controller;

	private HttpJob job(JobState current, JobState requested) {
		HttpJob job = new WideHttpJob();
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(current).requestedState(requested).build());
		when(queryService.getJob(5L)).thenReturn(job);
		return job;
	}

	@Test
	void pollAnswersWithTheRequestedState() {
		job(JobState.RUNNING, JobState.PAUSED);

		assertEquals(JobState.PAUSED, controller.pollJob(5L).getBody().requestedState());
	}

	@Test
	void pollAnswersWithTheCurrentStateWhenNothingIsRequested() {
		job(JobState.LOSTCONTACT, null);

		assertEquals(JobState.LOSTCONTACT, controller.pollJob(5L).getBody().requestedState());
	}

	@Test
	void pollRecordsTheContactOfTheWorker() {
		job(JobState.RUNNING, null);

		controller.pollJob(5L);

		verify(stateService).recordWorkerContact(5L);
	}

	@Test
	void pollOfAnUnknownJobIsReportedAndAsksNothingElse() {
		doThrow(new EntityNotFoundException()).when(stateService).recordWorkerContact(5L);

		assertThrows(EntityNotFoundException.class, () -> controller.pollJob(5L));

		verifyNoInteractions(queryService);
	}

	@Test
	void jobToUpdateIsTheOneInThePathNotTheOneInTheBody() {
		ResponseEntity<Void> response = controller.updateJob(5L, new JobStateUpdateRequest(99L, JobState.RUNNING, "msg", null));

		assertEquals(204, response.getStatusCode().value());
		verify(stateService).updateCurrentJobState(5L, JobState.RUNNING, "msg", null);
	}

	@Test
	void theFailureCategoryOfTheReportIsPassedAsAnEnum() {
		controller.updateJob(5L, new JobStateUpdateRequest(5L, JobState.FAILED, "boom", "INVALID_CONFIG"));

		verify(stateService).updateCurrentJobState(5L, JobState.FAILED, "boom",
				com.automata.job.domain.model.enums.FailureCategory.INVALID_CONFIG);
	}

	@Test
	void aFailureCategoryTheCoreDoesNotKnowIsPassedAsUnknown() {
		controller.updateJob(5L, new JobStateUpdateRequest(5L, JobState.FAILED, "boom", "SOMETHING_NEW"));

		verify(stateService).updateCurrentJobState(5L, JobState.FAILED, "boom",
				com.automata.job.domain.model.enums.FailureCategory.UNKNOWN);
	}

}
