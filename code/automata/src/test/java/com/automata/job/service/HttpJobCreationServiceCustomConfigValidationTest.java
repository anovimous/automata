package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.agentcontext.AgentContext;
import com.automata.agentcontext.AgentContextService;
import com.automata.host.Host;
import com.automata.job.api.dto.AiJobCreationRequest;
import com.automata.job.api.dto.GenericHttpJobDetailsDto;
import com.automata.job.api.dto.NarrowHttpJobDetailsDto;
import com.automata.job.api.dto.WideHttpJobDetailsDto;
import com.automata.job.domain.exception.InvalidCustomConfigException;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.repository.HttpJobRepository;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.routine.common.enums.Duration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class HttpJobCreationServiceCustomConfigValidationTest {

	private static final JsonNode CUSTOM_CONFIG = new ObjectMapper().createObjectNode().put("rawRequestBase64", "x");

	@Mock
	HttpJobRepository jobRepo;
	@Mock
	RoutineRepository routineRepo;
	@Mock
	RequestRepository requestRepo;
	@Mock
	TargetSelectionService targetSelectionService;
	@Mock
	JobTargetConfigService targetConfigService;
	@Mock
	JobConfigFileAsyncService configFileService;
	@Mock
	CustomConfigValidator customConfigValidator;
	@Mock
	AgentContextService agentContextService;

	@InjectMocks
	HttpJobCreationService service;

	private final Routine routine = RoutineFixtures.usableRoutine();

	private final InvalidCustomConfigException invalid = new InvalidCustomConfigException("fuzz", List.of("bad"));

	private static GenericHttpJobDetailsDto genericDto() {
		return new GenericHttpJobDetailsDto(ResultsVerbosity.NORMAL, 2, 10, 1L, null,
				new TargetSelector(SelectorType.SINGLE_REQUEST, null), List.of(), CUSTOM_CONFIG);
	}

	@Test
	void invalidCustomConfigOfANarrowJobIsRejectedBeforeAnythingIsSaved() {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));
		doThrow(invalid).when(customConfigValidator).validate(routine, CUSTOM_CONFIG);

		assertThrows(InvalidCustomConfigException.class,
				() -> service.createDraftNarrowJob(genericDto(), new NarrowHttpJobDetailsDto(Duration.SHORT, 4L, null)));

		verify(jobRepo, never()).save(any(HttpJob.class));
		verifyNoInteractions(targetSelectionService, targetConfigService, configFileService);
	}

	@Test
	void invalidCustomConfigOfAWideJobIsRejectedBeforeAnythingIsSaved() {
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));
		doThrow(invalid).when(customConfigValidator).validate(routine, CUSTOM_CONFIG);

		assertThrows(InvalidCustomConfigException.class,
				() -> service.createDraftWideJob(genericDto(), new WideHttpJobDetailsDto(2L)));

		verify(jobRepo, never()).save(any(HttpJob.class));
		verifyNoInteractions(targetSelectionService, targetConfigService, configFileService);
	}

	@Test
	void invalidCustomConfigOfAnAiJobIsRejectedBeforeAnythingIsSaved() {
		Host host = Host.of(4L);
		host.setHostRateLimit(10);
		Request request = Request.of(5L);
		request.setHost(host);
		routine.setId(1L);
		AgentContext context = AgentContext.builder().host(host).allowedRoutines(List.of(routine)).build();
		UUID contextUuid = UUID.randomUUID();
		when(agentContextService.getContextByUuid(contextUuid)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));
		when(agentContextService.resolveContextTargets(context.getId())).thenReturn(
				List.of(com.automata.agentcontext.common.dto.ResolvedTarget.builder().requestId(5L).build()));
		doThrow(invalid).when(customConfigValidator).validate(routine, CUSTOM_CONFIG);
		AiJobCreationRequest aiRequest = new AiJobCreationRequest(5L, "goal", 1L, Duration.SHORT, Set.of(),
				null, null, null, CUSTOM_CONFIG);

		assertThrows(InvalidCustomConfigException.class, () -> service.createDraftAiNarrowJob(contextUuid, aiRequest));

		verify(jobRepo, never()).save(any(HttpJob.class));
		verifyNoInteractions(targetSelectionService, targetConfigService, configFileService);
	}

}
