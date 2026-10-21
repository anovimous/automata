package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.automata.agentcontext.AgentContext;
import com.automata.agentcontext.AgentContextService;
import com.automata.agentcontext.common.dto.ResolvedTarget;
import com.automata.agentcontext.common.enums.AgentContextItemType;
import com.automata.host.Host;
import com.automata.job.api.dto.AiJobCreationRequest;
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.job.domain.valueobject.TargetSelectionResult;
import com.automata.job.repository.HttpJobRepository;
import com.automata.program.Program;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.routine.common.enums.Duration;
import com.automata.wordlist.WordlistRepository;

@ExtendWith(MockitoExtension.class)
class HttpJobCreationServiceAiJobTest {

	private static final UUID CONTEXT_UUID = UUID.randomUUID();

	@Mock
	HttpJobRepository jobRepo;
	@Mock
	RoutineRepository routineRepo;
	@Mock
	RequestRepository requestRepo;
	@Mock
	WordlistRepository wordlistRepo;
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

	private Host host;
	private Routine routine;
	private Request request;
	private AgentContext context;

	@BeforeEach
	void setUp() {
		TransactionSynchronizationManager.initSynchronization();
		host = Host.of(4L);
		host.setProgram(new Program());
		host.setHostRateLimit(50);
		routine = new Routine();
		routine.setId(1L);
		routine.setKey("fuzz");
		request = Request.of(5L);
		request.setHost(host);
		context = AgentContext.builder().host(host).allowedRoutines(List.of(routine)).build();
		context.setId(9L);
	}

	@AfterEach
	void clearTransactionSynchronization() {
		TransactionSynchronizationManager.clearSynchronization();
	}

	private AiJobCreationRequest aiRequest(Duration duration, Integer rate, ResultsVerbosity verbosity,
			Integer priority) {
		return new AiJobCreationRequest(5L, CONTEXT_UUID, "checks the id parameter", 1L, duration, Set.of(), rate,
				verbosity, priority, null);
	}

	private ResolvedTarget target(boolean autoApproval) {
		return ResolvedTarget.builder().requestId(5L).automataJobEligible(true).autoApproval(autoApproval)
				.sourceItemType(AgentContextItemType.REQUEST).build();
	}

	private void givenJobCanBeCreated(List<ResolvedTarget> resolvedTargets) {
		when(agentContextService.getContextByUuid(CONTEXT_UUID)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));
		when(agentContextService.resolveContextTargets(9L)).thenReturn(resolvedTargets);
		when(jobRepo.save(any(HttpJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(targetSelectionService.handleTargetSelector(any()))
				.thenReturn(new TargetSelectionResult.SingleRequest(5L, 4L));
		when(targetConfigService.configureRequestsAsTarget(any(), any()))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	private NarrowHttpJob createAndCapture(AiJobCreationRequest aiRequest) {
		service.createDraftAiNarrowJob(aiRequest);
		ArgumentCaptor<HttpJob> saved = ArgumentCaptor.forClass(HttpJob.class);
		verify(jobRepo).save(saved.capture());
		return (NarrowHttpJob) saved.getValue();
	}

	// ---- createDraftAiNarrowJob

	@Test
	void jobIsAnApprovedDraftLinkedToTheContextWhenTheTargetIsAutoApproved() {
		givenJobCanBeCreated(List.of(target(true)));

		NarrowHttpJob job = createAndCapture(aiRequest(Duration.SHORT, 20, ResultsVerbosity.NORMAL, 2));

		assertSame(context, job.getAgentContext());
		assertEquals(JobCreator.AI, job.getCreatedBy());
		assertTrue(job.isApproved());
		assertEquals(JobState.DRAFT, job.getGenericDetails().getCurrentState());
		assertEquals("checks the id parameter", job.getAiDescription());
		assertSame(routine, job.getRoutine());
		assertSame(host, job.getHost());
		assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
	}

	@Test
	void jobNeedsApprovalWhenTheTargetIsNotAutoApproved() {
		givenJobCanBeCreated(List.of(target(false)));

		assertFalse(createAndCapture(aiRequest(Duration.SHORT, null, null, null)).isApproved());
	}

	@Test
	void jobNeedsApprovalWhenTheRequestIsNotAResolvedTargetOfTheContext() {
		givenJobCanBeCreated(List.of());

		assertFalse(createAndCapture(aiRequest(Duration.SHORT, null, null, null)).isApproved());
	}

	@Test
	void contextDefaultsFillWhatTheRequestLeavesOut() {
		context.setDefaultDuration(Duration.LONG);
		context.setDefaultVerbosity(ResultsVerbosity.NORMAL);
		context.setDefaultPriority(2);
		givenJobCanBeCreated(List.of(target(true)));

		NarrowHttpJob job = createAndCapture(aiRequest(null, null, null, null));

		assertEquals(Duration.LONG, job.getDuration());
		assertEquals(ResultsVerbosity.NORMAL, job.getGenericDetails().getVerbosity());
		assertEquals(2, job.getGenericDetails().getPriority());
	}

	@Test
	void valuesOfTheRequestWinOverTheContextDefaults() {
		context.setDefaultDuration(Duration.LONG);
		context.setDefaultVerbosity(ResultsVerbosity.NORMAL);
		context.setDefaultPriority(2);
		givenJobCanBeCreated(List.of(target(true)));

		NarrowHttpJob job = createAndCapture(aiRequest(Duration.SHORT, null, ResultsVerbosity.VERBOSE, 1));

		assertEquals(Duration.SHORT, job.getDuration());
		assertEquals(ResultsVerbosity.VERBOSE, job.getGenericDetails().getVerbosity());
		assertEquals(1, job.getGenericDetails().getPriority());
	}

	@Test
	void verbosityAndPriorityFallBackToSummarizedAndOneWhenNobodyGivesThem() {
		givenJobCanBeCreated(List.of(target(true)));

		NarrowHttpJob job = createAndCapture(aiRequest(Duration.SHORT, null, null, null));

		assertEquals(ResultsVerbosity.SUMMARIZED, job.getGenericDetails().getVerbosity());
		assertEquals(1, job.getGenericDetails().getPriority());
	}

	@Test
	void rateIsCappedByTheContextMaxRate() {
		context.setMaxRate(10);
		givenJobCanBeCreated(List.of(target(true)));

		assertEquals(10, createAndCapture(aiRequest(Duration.SHORT, 30, null, null)).getGenericDetails().getRate());
	}

	@Test
	void durationIsRequiredWhenNeitherTheRequestNorTheContextGivesIt() {
		when(agentContextService.getContextByUuid(CONTEXT_UUID)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));

		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftAiNarrowJob(aiRequest(null, null, null, null)));

		verify(jobRepo, never()).save(any(HttpJob.class));
	}

	@Test
	void routineOutsideTheAllowedRoutinesOfTheContextIsRejected() {
		context.setAllowedRoutines(List.of());
		when(agentContextService.getContextByUuid(CONTEXT_UUID)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));

		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftAiNarrowJob(aiRequest(Duration.SHORT, null, null, null)));

		verify(jobRepo, never()).save(any(HttpJob.class));
	}

	@Test
	void requestOfAnotherHostThanTheContextIsRejected() {
		context.setHost(Host.of(99L));
		when(agentContextService.getContextByUuid(CONTEXT_UUID)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));

		assertThrows(IneligibleJobTargetException.class,
				() -> service.createDraftAiNarrowJob(aiRequest(Duration.SHORT, null, null, null)));

		verify(jobRepo, never()).save(any(HttpJob.class));
	}

	@Test
	void unknownContextIsNotFound() {
		when(agentContextService.getContextByUuid(CONTEXT_UUID))
				.thenThrow(new jakarta.persistence.EntityNotFoundException("AgentContext not found"));

		assertThrows(jakarta.persistence.EntityNotFoundException.class,
				() -> service.createDraftAiNarrowJob(aiRequest(Duration.SHORT, null, null, null)));
	}

	// ---- requireAiJobFields

	@Test
	void everyRequiredFieldOfTheRequestIsChecked() {
		Set<Long> none = Set.of();
		List<AiJobCreationRequest> invalid = List.of(
				new AiJobCreationRequest(5L, null, "goal", 1L, null, none, null, null, null, null),
				new AiJobCreationRequest(null, CONTEXT_UUID, "goal", 1L, null, none, null, null, null, null),
				new AiJobCreationRequest(5L, CONTEXT_UUID, "goal", null, null, none, null, null, null, null),
				new AiJobCreationRequest(5L, CONTEXT_UUID, null, 1L, null, none, null, null, null, null),
				new AiJobCreationRequest(5L, CONTEXT_UUID, "  ", 1L, null, none, null, null, null, null),
				new AiJobCreationRequest(5L, CONTEXT_UUID, "goal", 1L, null, null, null, null, null, null));

		for (AiJobCreationRequest aiRequest : invalid)
			assertThrows(IllegalArgumentException.class, () -> service.requireAiJobFields(aiRequest));
	}

	@Test
	void aCompleteRequestPassesTheFieldCheck() {
		service.requireAiJobFields(aiRequest(null, null, null, null));
	}

	// ---- requireRoutineAllowedInContext

	@Test
	void allowedRoutinePassesTheContextCheck() {
		service.requireRoutineAllowedInContext(routine, context);
	}

	@Test
	void routineNotInTheContextFailsTheContextCheck() {
		Routine other = new Routine();
		other.setId(2L);
		other.setKey("masser");

		assertThrows(IllegalArgumentException.class, () -> service.requireRoutineAllowedInContext(other, context));
	}

	// ---- isAutoApproved

	@Test
	void autoApprovalOfTheResolvedTargetIsUsed() {
		when(agentContextService.resolveContextTargets(9L))
				.thenReturn(List.of(ResolvedTarget.builder().requestId(6L).autoApproval(false).build(), target(true)));

		assertTrue(service.isAutoApproved(context, 5L));
	}

	@Test
	void aRequestThatIsNotAResolvedTargetIsNotAutoApproved() {
		when(agentContextService.resolveContextTargets(9L)).thenReturn(List.of(target(true)));

		assertFalse(service.isAutoApproved(context, 77L));
	}

	// ---- cappedRate

	@Test
	void requestedRateIsKeptWhenBelowTheLimits() {
		assertEquals(5, service.cappedRate(5, 50, 20));
	}

	@Test
	void requestedRateIsCappedByTheHostRateLimit() {
		assertEquals(50, service.cappedRate(80, 50, null));
	}

	@Test
	void requestedRateIsCappedByTheContextMaxRate() {
		assertEquals(20, service.cappedRate(80, 100, 20));
	}

	@Test
	void hostRateLimitOfZeroMeansNoLimit() {
		assertEquals(80, service.cappedRate(80, 0, null));
	}

	@Test
	void withoutARequestedRateTheHostRateLimitIsUsed() {
		assertEquals(50, service.cappedRate(null, 50, null));
	}

	@Test
	void withoutARequestedRateTheContextMaxRateCapsTheHostRateLimit() {
		assertEquals(20, service.cappedRate(null, 50, 20));
	}

	@Test
	void withoutARequestedRateAndAHostLimitTheContextMaxRateIsUsed() {
		assertEquals(20, service.cappedRate(null, 0, 20));
	}

}
