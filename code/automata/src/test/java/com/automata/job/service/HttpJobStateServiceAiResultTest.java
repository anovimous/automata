package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.repository.HttpJobRepository;

@ExtendWith(MockitoExtension.class)
class HttpJobStateServiceAiResultTest {

	@Mock
	HttpJobRepository jobRepo;
	@Mock
	JobConfigFileAsyncService configFileService;

	@InjectMocks
	HttpJobStateService service;

	private NarrowHttpJob aiJob(JobState state) {
		NarrowHttpJob job = new NarrowHttpJob();
		job.setId(1L);
		job.setCreatedBy(JobCreator.AI);
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(state).build());
		when(jobRepo.findById(1L)).thenReturn(Optional.of(job));
		return job;
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "FINISHED", "FAILED", "SELFTERMINATED", "CANCELED", "LOSTCONTACT" })
	void aiResultCanBeSetOnTerminalStates(JobState state) {
		NarrowHttpJob job = aiJob(state);

		service.setAiResult(1L, "analysis complete");

		assertEquals("analysis complete", job.getAiResult());
		verify(jobRepo).save(job);
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "DRAFT", "TOQUEUE", "SCHEDULED", "QUEUED", "RUNNING", "PAUSED" })
	void aiResultCannotBeSetOnNonTerminalStates(JobState state) {
		aiJob(state);

		assertThrows(InvalidJobStateException.class, () -> service.setAiResult(1L, "result"));
	}

	@Test
	void aiResultRejectsUserCreatedJob() {
		NarrowHttpJob job = new NarrowHttpJob();
		job.setId(2L);
		job.setCreatedBy(JobCreator.USER);
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(JobState.FINISHED).build());
		when(jobRepo.findById(2L)).thenReturn(Optional.of(job));

		assertThrows(InvalidJobStateException.class, () -> service.setAiResult(2L, "result"));
	}

}
