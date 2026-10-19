package com.automata.job.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.automata.job.api.dto.GenericHttpJobDetailsDto;
import com.automata.job.api.dto.HttpJobCreationRequest;
import com.automata.job.api.dto.NarrowHttpJobDetailsDto;
import com.automata.job.api.dto.WideHttpJobDetailsDto;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.service.HttpJobCreationService;
import com.automata.job.service.HttpJobQueryService;
import com.automata.job.service.HttpJobStateService;
import com.automata.job.service.HttpJobsSynchronizationService;

class HttpJobControllerCreateNewJobTest {

	private final HttpJobCreationService creationService = mock(HttpJobCreationService.class);

	private final HttpJobController controller = new HttpJobController(creationService,
			mock(HttpJobQueryService.class), mock(HttpJobStateService.class),
			mock(HttpJobsSynchronizationService.class));

	private final GenericHttpJobDetailsDto generic = mock(GenericHttpJobDetailsDto.class);

	private <T extends HttpJob> T jobWithId(Class<T> type, long id) {
		T job = mock(type);
		when(job.getId()).thenReturn(id);
		return job;
	}

	@Test
	void narrowScopeCreatesNarrowJob() {
		NarrowHttpJobDetailsDto narrow = mock(NarrowHttpJobDetailsDto.class);
		NarrowHttpJob job = jobWithId(NarrowHttpJob.class, 5L);
		when(creationService.createDraftNarrowJob(generic, narrow)).thenReturn(job);

		ResponseEntity<Void> response = controller
				.createNewJob(new HttpJobCreationRequest(generic, HttpJobScope.NARROW, narrow, null));

		assertEquals(201, response.getStatusCode().value());
		assertEquals("/api/jobs/http/5", response.getHeaders().getLocation().toString());
		verify(creationService).createDraftNarrowJob(generic, narrow);
	}

	@Test
	void wideScopeCreatesWideJob() {
		WideHttpJobDetailsDto wide = mock(WideHttpJobDetailsDto.class);
		WideHttpJob job = jobWithId(WideHttpJob.class, 6L);
		when(creationService.createDraftWideJob(generic, wide)).thenReturn(job);

		ResponseEntity<Void> response = controller
				.createNewJob(new HttpJobCreationRequest(generic, HttpJobScope.WIDE, null, wide));

		assertEquals("/api/jobs/http/6", response.getHeaders().getLocation().toString());
		verify(creationService).createDraftWideJob(generic, wide);
	}

	@Test
	void globalScopeNoLongerExists() {
		assertEquals(2, HttpJobScope.values().length);
	}
}
