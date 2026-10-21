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
import com.automata.job.infra.S3Service;
import com.automata.job.repository.HttpJobRepository;
import com.automata.routine.Routine;

import io.awspring.cloud.s3.S3Resource;
import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class HttpJobQueryServiceTest {

	@Mock
	HttpJobRepository jobRepo;
	@Mock
	S3Service s3;

	@InjectMocks
	HttpJobQueryService service;

	private HttpJob job(JobState state) {
		HttpJob job = new WideHttpJob();
		job.setId(1L);
		job.setGenericDetails(HttpJobGenericDetails.builder().currentState(state).build());
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

		Page<HttpJobSummaryInternalDto> page = service.getAllJobs(new HttpJobFilter(null, null, null, null, null, null),
				pageable);

		assertEquals(41, page.getTotalElements());
		assertEquals(List.of(new HttpJobSummaryInternalDto(1L, Instant.EPOCH, HttpJobScope.WIDE, JobState.RUNNING, 3,
				40, 7L, "routine-key", JobCreator.USER, true)), page.getContent());
	}

	// ---- getJobFullDetails

	@Test
	void fullDetailsAreReturned() {
		HttpJobFullDetailsInternalDto dto = new HttpJobFullDetailsInternalDto(1L, null, JobState.DRAFT, null, null,
				1, 1, null, null, null, 1L, "p", 1L, "r", null, null, null, null, null, JobCreator.USER, true);
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

		assertSame(resource, service.getJobResult(1L));
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

}
