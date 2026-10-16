package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.host.HostRepository;
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.repository.HttpJobRepository;
import com.automata.job.repository.NarrowJobTargetRequestRepository;
import com.automata.job.repository.WideJobTargetHostRepository;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.common.enums.RequestContentType;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class JobTargetConfigServiceTest {

	@Mock
	HttpJobRepository jobRepo;
	@Mock
	HostRepository hostRepo;
	@Mock
	RequestRepository requestRepo;
	@Mock
	NarrowJobTargetRequestRepository jobTargetRequestRepo;
	@Mock
	WideJobTargetHostRepository jobTargetHostRepo;

	@InjectMocks
	JobTargetConfigService service;

	private static Request request(Long id, RequestContentType contentType, long size) {
		Request request = Request.of(id);
		request.setContentType(contentType);
		request.setBodySizeBytes(size);
		return request;
	}

	@Test
	void eligibleRequestsAreConfiguredAsTargets() {
		NarrowHttpJob job = new NarrowHttpJob();
		when(requestRepo.findAllById(any())).thenReturn(
				List.of(request(1L, RequestContentType.JSON, 3), request(2L, null, 0)));
		when(jobRepo.save(job)).thenReturn(job);

		NarrowHttpJob result = service.configureRequestsAsTarget(job, Set.of(1L, 2L));

		assertEquals(2, result.getTargetConfig().getTargetRequests().size());
		verify(jobTargetRequestRepo).saveAll(any());
	}

	@Test
	void ineligibleRequestRejectsWholeConfiguration() {
		NarrowHttpJob job = new NarrowHttpJob();
		when(requestRepo.findAllById(any())).thenReturn(
				List.of(request(1L, RequestContentType.JSON, 3), request(2L, RequestContentType.MULTIPART, 3)));

		assertThrows(IneligibleJobTargetException.class, () -> service.configureRequestsAsTarget(job, Set.of(1L, 2L)));

		verify(jobTargetRequestRepo, never()).saveAll(any());
		verify(jobRepo, never()).save(any());
	}

	@Test
	void missingRequestIsNotFound() {
		NarrowHttpJob job = new NarrowHttpJob();
		when(requestRepo.findAllById(any())).thenReturn(List.of(request(1L, RequestContentType.JSON, 3)));

		assertThrows(EntityNotFoundException.class, () -> service.configureRequestsAsTarget(job, Set.of(1L, 2L)));

		verify(jobTargetRequestRepo, never()).saveAll(any());
	}

}
