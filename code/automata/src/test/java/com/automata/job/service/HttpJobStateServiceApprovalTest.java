package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.exception.JobNotApprovedException;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.ConfigFileStatus;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.repository.HttpJobRepository;

@ExtendWith(MockitoExtension.class)
class HttpJobStateServiceApprovalTest {

	@Mock
	HttpJobRepository jobRepo;
	@Mock
	JobConfigFileAsyncService configFileService;

	@InjectMocks
	HttpJobStateService service;

	private NarrowHttpJob aiDraftJob() {
		NarrowHttpJob job = new NarrowHttpJob();
		job.setId(1L);
		job.setCreatedBy(JobCreator.AI);
		job.setApproved(false);
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(JobState.DRAFT)
				.configFileStatus(ConfigFileStatus.READY).build());
		when(jobRepo.findById(1L)).thenReturn(Optional.of(job));
		return job;
	}

	@Test
	void unapprovedAiJobCannotBeQueued() {
		aiDraftJob();

		assertThrows(JobNotApprovedException.class, () -> service.prepareJobForQueueing(1L));
	}

	@Test
	void approvedAiJobCanBeQueued() {
		NarrowHttpJob job = aiDraftJob();
		job.setApproved(true);

		service.prepareJobForQueueing(1L);

		assertEquals(JobState.TOQUEUE, job.getGenericDetails().getCurrentState());
	}

	@Test
	void userCreatedJobBypassesApprovalCheck() {
		NarrowHttpJob job = new NarrowHttpJob();
		job.setId(2L);
		job.setCreatedBy(JobCreator.USER);
		job.setApproved(true);
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(JobState.DRAFT)
				.configFileStatus(ConfigFileStatus.READY).build());
		when(jobRepo.findById(2L)).thenReturn(Optional.of(job));

		service.prepareJobForQueueing(2L);

		assertEquals(JobState.TOQUEUE, job.getGenericDetails().getCurrentState());
	}

	@Test
	void approveSetsFlagToTrue() {
		NarrowHttpJob job = aiDraftJob();

		service.approveJob(1L);

		assertTrue(job.isApproved());
		verify(jobRepo).save(job);
	}

	@Test
	void approveRejectsNonAiJob() {
		NarrowHttpJob job = new NarrowHttpJob();
		job.setId(3L);
		job.setCreatedBy(JobCreator.USER);
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(JobState.DRAFT).build());
		when(jobRepo.findById(3L)).thenReturn(Optional.of(job));

		assertThrows(InvalidJobStateException.class, () -> service.approveJob(3L));
	}

	@Test
	void approveRejectsNonDraftJob() {
		NarrowHttpJob job = new NarrowHttpJob();
		job.setId(4L);
		job.setCreatedBy(JobCreator.AI);
		job.setApproved(false);
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(JobState.RUNNING).build());
		when(jobRepo.findById(4L)).thenReturn(Optional.of(job));

		assertThrows(InvalidJobStateException.class, () -> service.approveJob(4L));
	}

}
