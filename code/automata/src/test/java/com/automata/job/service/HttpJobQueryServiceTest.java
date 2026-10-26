package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.automata.job.api.dto.HttpJobFilter;
import com.automata.job.domain.exception.InvalidJobStateException;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
import com.automata.job.domain.valueobject.HttpJobSummaryInternalDto;
import com.automata.job.domain.valueobject.JobResultFile;
import com.automata.job.infra.S3Service;
import com.automata.job.repository.HttpJobRepository;
import com.automata.routine.Routine;
import com.automata.routine.common.enums.ResultContentType;

import io.awspring.cloud.s3.S3Resource;
import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class HttpJobQueryServiceTest {

	@Mock
	HttpJobRepository jobRepo;
	@Mock
	S3Service s3;
	@Mock
	com.automata.job.repository.NarrowJobTargetRequestRepository targetRequestRepo;

	@InjectMocks
	HttpJobQueryService service;

	private HttpJob job(JobState state) {
		HttpJob job = new WideHttpJob();
		job.setId(1L);
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(state).build());
		Routine routine = new Routine();
		routine.setResultContentType(ResultContentType.JSON);
		job.setRoutine(routine);
		when(jobRepo.findById(1L)).thenReturn(Optional.of(job));
		return job;
	}

	// ---- getJob

	@Test
	void jobIsReturned() {
		HttpJob job = job(JobState.DRAFT);

		assertSame(job, service.getJob(1L));
	}

	@Test
	void unknownJobIsNotFound() {
		when(jobRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getJob(1L));
	}

	// ---- getAllJobs

	@Test
	void summariesAreReadWithTheFilterAsASpecificationAndTheGivenPageable() {
		Pageable pageable = PageRequest.of(1, 20);
		HttpJob job = new WideHttpJob();
		job.setId(1L);
		job.setCreationDate(Instant.EPOCH);
		job.setGenericDetails(HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.WIDE)
				.currentState(JobState.RUNNING).priority(3).rate(40).build());
		Routine routine = new Routine();
		routine.setId(7L);
		routine.setKey("routine-key");
		job.setRoutine(routine);
		when(jobRepo.findAll(any(Specification.class), eq(pageable)))
				.thenReturn(new PageImpl<>(List.of(job), pageable, 41));

		Page<HttpJobSummaryInternalDto> page = service.getAllJobs(new HttpJobFilter(null, null, null, null, null, null, null, null),
				pageable);

		assertEquals(41, page.getTotalElements());
		assertEquals(List.of(new HttpJobSummaryInternalDto(1L, Instant.EPOCH, HttpJobScope.WIDE, JobState.RUNNING, 3,
				40, 7L, "routine-key", JobCreator.USER, true, false, false)), page.getContent());
	}

	// ---- getJobFullDetails

	@Test
	void fullDetailsAreReturned() {
		HttpJobFullDetailsInternalDto dto = new HttpJobFullDetailsInternalDto(1L, null, JobState.DRAFT, null, null,
				1, 1, null, null, null, 1L, "p", 1L, "r", null, null, null, null, null, JobCreator.USER, true, null);
		when(jobRepo.findFullDetailsByJobId(1L)).thenReturn(Optional.of(dto));

		assertSame(dto, service.getJobFullDetails(1L));
	}

	@Test
	void fullDetailsOfAnUnknownJobAreNotFound() {
		when(jobRepo.findFullDetailsByJobId(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getJobFullDetails(1L));
	}

	// ---- getJobResult

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "FINISHED", "FAILED" }, mode = EnumSource.Mode.EXCLUDE)
	void resultOfAJobWhichDidNotEndIsRefused(JobState state) {
		job(state);

		assertThrows(InvalidJobStateException.class, () -> service.getJobResult(1L));

		verifyNoInteractions(s3);
	}

	@ParameterizedTest
	@EnumSource(value = JobState.class, names = { "FINISHED", "FAILED" })
	void resultOfAnEndedJobIsReturned(JobState state) {
		job(state);
		S3Resource resource = org.mockito.Mockito.mock(S3Resource.class);
		when(resource.exists()).thenReturn(true);
		when(s3.getJobResult(1L)).thenReturn(resource);

		JobResultFile result = service.getJobResult(1L);

		assertSame(resource, result.resource());
		assertEquals(ResultContentType.JSON, result.contentType());
	}

	@Test
	void missingResultIsNotFound() {
		job(JobState.FINISHED);
		S3Resource resource = org.mockito.Mockito.mock(S3Resource.class);
		when(resource.exists()).thenReturn(false);
		when(s3.getJobResult(1L)).thenReturn(resource);

		EntityNotFoundException e = assertThrows(EntityNotFoundException.class, () -> service.getJobResult(1L));

		assertEquals("Result not found", e.getMessage());
		verify(s3).getJobResult(1L);
	}

	// ---- getJobOfContext

	private static final java.util.UUID CONTEXT_UUID = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111");

	private HttpJob aiJobOf(java.util.UUID contextUuid) {
		HttpJob job = job(JobState.DRAFT);
		job.setCreatedBy(JobCreator.AI);
		job.setAgentContext(com.automata.agentcontext.AgentContext.builder().uuid(contextUuid).build());
		return job;
	}

	@Test
	void aiJobOfTheContextIsReturned() {
		HttpJob job = aiJobOf(CONTEXT_UUID);

		assertSame(job, service.getJobOfContext(CONTEXT_UUID, 1L));
	}

	@Test
	void aiJobOfAnotherContextIsNotFound() {
		aiJobOf(java.util.UUID.randomUUID());

		assertThrows(EntityNotFoundException.class, () -> service.getJobOfContext(CONTEXT_UUID, 1L));
	}

	@Test
	void jobCreatedByAUserIsNotFound() {
		HttpJob job = aiJobOf(CONTEXT_UUID);
		job.setCreatedBy(JobCreator.USER);

		assertThrows(EntityNotFoundException.class, () -> service.getJobOfContext(CONTEXT_UUID, 1L));
	}

	@Test
	void aiJobWithoutAContextIsNotFound() {
		HttpJob job = aiJobOf(CONTEXT_UUID);
		job.setAgentContext(null);

		assertThrows(EntityNotFoundException.class, () -> service.getJobOfContext(CONTEXT_UUID, 1L));
	}

	@Test
	void unknownJobOfTheContextIsNotFound() {
		when(jobRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getJobOfContext(CONTEXT_UUID, 1L));
	}

	// ---- getJobsOfRequest

	@Test
	void jobsOfARequestKeepTheirOriginRelativeToTheContext() {
		Pageable pageable = PageRequest.of(0, 20);
		HttpJob own = new WideHttpJob();
		own.setId(1L);
		own.setCreatedBy(JobCreator.AI);
		own.setAgentContext(com.automata.agentcontext.AgentContext.builder().uuid(CONTEXT_UUID).build());
		own.setGenericDetails(HttpJobGenericDetails.builder().currentState(JobState.DRAFT).build());
		own.setRoutine(new Routine());
		HttpJob foreign = new WideHttpJob();
		foreign.setId(2L);
		foreign.setCreatedBy(JobCreator.AI);
		foreign.setAgentContext(com.automata.agentcontext.AgentContext.builder().uuid(java.util.UUID.randomUUID()).build());
		foreign.setGenericDetails(HttpJobGenericDetails.builder().currentState(JobState.DRAFT).build());
		foreign.setRoutine(own.getRoutine());
		own.setCreationDate(Instant.EPOCH);
		foreign.setCreationDate(Instant.EPOCH);
		own.getGenericDetails().setHttpJobScope(HttpJobScope.NARROW);
		foreign.getGenericDetails().setHttpJobScope(HttpJobScope.NARROW);
		when(jobRepo.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(own, foreign)));

		Page<com.automata.job.domain.valueobject.RequestJobSummaryInternalDto> page = service
				.getJobsOfRequest(CONTEXT_UUID, 9L, null, pageable);

		assertEquals(2, page.getTotalElements());
		assertEquals(com.automata.job.domain.model.enums.JobOrigin.THIS_CONTEXT, page.getContent().get(0).origin());
		assertEquals(com.automata.job.domain.model.enums.JobOrigin.OTHER_CONTEXT, page.getContent().get(1).origin());
	}

	// ---- getJobOfRequest

	@Test
	void jobThatTargetedTheRequestIsReturnedWhateverItsContext() {
		HttpJob job = aiJobOf(java.util.UUID.randomUUID());
		when(targetRequestRepo.existsByJobIdAndTargetRequestId(1L, 9L)).thenReturn(true);

		assertSame(job, service.getJobOfRequest(9L, 1L));
	}

	@Test
	void jobThatDidNotTargetTheRequestIsNotFound() {
		aiJobOf(CONTEXT_UUID);
		when(targetRequestRepo.existsByJobIdAndTargetRequestId(1L, 9L)).thenReturn(false);

		assertThrows(EntityNotFoundException.class, () -> service.getJobOfRequest(9L, 1L));
	}

	@Test
	void unknownJobOfARequestIsNotFound() {
		when(jobRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getJobOfRequest(9L, 1L));
		verifyNoInteractions(targetRequestRepo);
	}

}
