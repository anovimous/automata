package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
		routine = RoutineFixtures.usableRoutine();
		routine.setId(1L);
		routine.setKey("fuzz");
		request = Request.of(5L);
		request.setHost(host);
		context = AgentContext.builder().host(host).allowedRoutines(List.of(routine)).maxRate(100).build();
		context.setId(9L);
	}

	@AfterEach
	void clearTransactionSynchronization() {
		TransactionSynchronizationManager.clearSynchronization();
	}

	private AiJobCreationRequest aiRequest(Duration duration, Integer rate, ResultsVerbosity verbosity,
			Integer priority) {
		return new AiJobCreationRequest(5L, "checks the id parameter", 1L, duration, Set.of(), rate,
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
		service.createDraftAiNarrowJob(CONTEXT_UUID, aiRequest);
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

	private void givenOnlyTheTargetsAreResolved(List<ResolvedTarget> resolvedTargets) {
		when(agentContextService.getContextByUuid(CONTEXT_UUID)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		when(agentContextService.resolveContextTargets(9L)).thenReturn(resolvedTargets);
	}

	@Test
	void requestThatIsNotAResolvedTargetOfTheContextIsNotFoundAndNothingIsSaved() {
		givenOnlyTheTargetsAreResolved(List.of());

		assertThrows(jakarta.persistence.EntityNotFoundException.class,
				() -> service.createDraftAiNarrowJob(CONTEXT_UUID, aiRequest(Duration.SHORT, null, null, null)));

		verify(jobRepo, never()).save(any(HttpJob.class));
		verifyNoInteractions(targetSelectionService, targetConfigService, configFileService);
	}

	@Test
	void onlyTheResolvedTargetsOfTheContextCanBeTheTarget() {
		givenOnlyTheTargetsAreResolved(List.of(ResolvedTarget.builder().requestId(6L).autoApproval(true).build()));

		assertThrows(jakarta.persistence.EntityNotFoundException.class,
				() -> service.createDraftAiNarrowJob(CONTEXT_UUID, aiRequest(Duration.SHORT, null, null, null)));

		verify(jobRepo, never()).save(any(HttpJob.class));
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
		when(agentContextService.resolveContextTargets(9L)).thenReturn(List.of(target(true)));
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));

		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftAiNarrowJob(CONTEXT_UUID, aiRequest(null, null, null, null)));

		verify(jobRepo, never()).save(any(HttpJob.class));
	}

	@Test
	void routineOutsideTheAllowedRoutinesOfTheContextIsRejected() {
		context.setAllowedRoutines(List.of());
		when(agentContextService.getContextByUuid(CONTEXT_UUID)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		when(agentContextService.resolveContextTargets(9L)).thenReturn(List.of(target(true)));
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));

		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftAiNarrowJob(CONTEXT_UUID, aiRequest(Duration.SHORT, null, null, null)));

		verify(jobRepo, never()).save(any(HttpJob.class));
	}

	@Test
	void requestOfAnotherHostThanTheContextIsRejected() {
		context.setHost(Host.of(99L));
		when(agentContextService.getContextByUuid(CONTEXT_UUID)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		when(agentContextService.resolveContextTargets(9L)).thenReturn(List.of(target(true)));

		assertThrows(IneligibleJobTargetException.class,
				() -> service.createDraftAiNarrowJob(CONTEXT_UUID, aiRequest(Duration.SHORT, null, null, null)));

		verify(jobRepo, never()).save(any(HttpJob.class));
	}

	@Test
	void unknownContextIsNotFound() {
		when(agentContextService.getContextByUuid(CONTEXT_UUID))
				.thenThrow(new jakarta.persistence.EntityNotFoundException("AgentContext not found"));

		assertThrows(jakarta.persistence.EntityNotFoundException.class,
				() -> service.createDraftAiNarrowJob(CONTEXT_UUID, aiRequest(Duration.SHORT, null, null, null)));
	}

	// ---- requireAiJobFields

	@Test
	void everyRequiredFieldOfTheRequestIsChecked() {
		Set<Long> none = Set.of();
		List<AiJobCreationRequest> invalid = List.of(
				new AiJobCreationRequest(null, "goal", 1L, null, none, null, null, null, null),
				new AiJobCreationRequest(5L, "goal", null, null, none, null, null, null, null),
				new AiJobCreationRequest(5L, null, 1L, null, none, null, null, null, null),
				new AiJobCreationRequest(5L, "  ", 1L, null, none, null, null, null, null),
				new AiJobCreationRequest(5L, "goal", 1L, null, null, null, null, null, null));

		for (AiJobCreationRequest aiRequest : invalid)
			assertThrows(IllegalArgumentException.class, () -> service.requireAiJobFields(aiRequest));
	}

	@Test
	void aDescriptionOverTheLimitIsRejected() {
		AiJobCreationRequest tooLong = new AiJobCreationRequest(5L, "x".repeat(HttpJob.MAX_AI_DESCRIPTION_LENGTH + 1),
				1L, null, Set.of(), null, null, null, null);

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.requireAiJobFields(tooLong));
		assertTrue(e.getMessage().contains("1000"));
	}

	@Test
	void aDescriptionAtTheLimitPasses() {
		service.requireAiJobFields(new AiJobCreationRequest(5L, "x".repeat(HttpJob.MAX_AI_DESCRIPTION_LENGTH), 1L,
				null, Set.of(), null, null, null, null));
	}

	@Test
	void aCompleteRequestPassesTheFieldCheck() {
		service.requireAiJobFields(aiRequest(null, null, null, null));
	}

	// ---- rate, priority, wordlists and routine rules

	@Test
	void aRateBelowOneIsRejectedByTheFieldCheck() {
		assertThrows(IllegalArgumentException.class, () -> service.requireAiJobFields(aiRequest(null, 0, null, null)));
		assertThrows(IllegalArgumentException.class,
				() -> service.requireAiJobFields(aiRequest(null, -3, null, null)));
	}

	@Test
	void aPriorityOutsideTheRangeIsRejectedByTheFieldCheck() {
		assertThrows(IllegalArgumentException.class, () -> service.requireAiJobFields(aiRequest(null, null, null, 0)));
		assertThrows(IllegalArgumentException.class, () -> service.requireAiJobFields(aiRequest(null, null, null, 6)));
	}

	@Test
	void aRateOfOneAndTheBoundsOfThePriorityPassTheFieldCheck() {
		service.requireAiJobFields(aiRequest(null, 1, null, HttpJob.MIN_PRIORITY));
		service.requireAiJobFields(aiRequest(null, 1, null, HttpJob.MAX_PRIORITY));
	}

	@Test
	void aContextDefaultPriorityOutsideTheRangeIsRejected() {
		context.setDefaultPriority(9);
		when(agentContextService.getContextByUuid(CONTEXT_UUID)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));
		when(agentContextService.resolveContextTargets(9L)).thenReturn(List.of(target(true)));

		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftAiNarrowJob(CONTEXT_UUID, aiRequest(Duration.SHORT, null, null, null)));

		verify(jobRepo, never()).save(any());
	}

	@Test
	void aJobOnAHostWithARateLimitOfZeroIsRefusedBeforeAnythingIsSaved() {
		host.setHostRateLimit(0);
		when(agentContextService.getContextByUuid(CONTEXT_UUID)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		when(agentContextService.resolveContextTargets(9L)).thenReturn(List.of(target(true)));

		com.automata.job.domain.exception.IneligibleJobTargetException e = assertThrows(
				com.automata.job.domain.exception.IneligibleJobTargetException.class,
				() -> service.createDraftAiNarrowJob(CONTEXT_UUID, aiRequest(Duration.SHORT, 5, null, null)));

		assertTrue(e.getMessage().contains("rate limit of 0"));
		verify(jobRepo, never()).save(any());
	}

	@Test
	void withoutARequestedRateTheHostRateLimitCappedByTheContextIsTheRate() {
		host.setHostRateLimit(50);
		context.setMaxRate(20);
		givenJobCanBeCreated(List.of(target(true)));

		assertEquals(20, createAndCapture(aiRequest(Duration.SHORT, null, null, null)).getGenericDetails().getRate());
	}

	@Test
	void aRateOfZeroIsRefusedEvenWhenTheHostHasALimit() {
		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftAiNarrowJob(CONTEXT_UUID, aiRequest(Duration.SHORT, 0, null, null)));

		verify(jobRepo, never()).save(any());
	}

	@Test
	void aRoutineNotAvailableAtTheWorkerIsRejectedBeforeAnythingIsSaved() {
		routine.setAvailableAtConsumer(false);
		when(agentContextService.getContextByUuid(CONTEXT_UUID)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));
		when(agentContextService.resolveContextTargets(9L)).thenReturn(List.of(target(true)));

		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftAiNarrowJob(CONTEXT_UUID, aiRequest(Duration.SHORT, null, null, null)));

		verify(jobRepo, never()).save(any());
	}

	@Test
	void aRoutineThatDoesNotAllowASingleRequestIsRejectedBeforeAnythingIsSaved() {
		routine.setAllowedTargets(java.util.EnumSet.of(com.automata.routine.common.enums.TargetCardinalityPair.SINGLE_HOST));
		when(agentContextService.getContextByUuid(CONTEXT_UUID)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));
		when(agentContextService.resolveContextTargets(9L)).thenReturn(List.of(target(true)));

		assertThrows(IllegalArgumentException.class,
				() -> service.createDraftAiNarrowJob(CONTEXT_UUID, aiRequest(Duration.SHORT, null, null, null)));

		verify(jobRepo, never()).save(any());
	}

	@Test
	void anUnknownWordlistIsNotFoundAndNothingIsConfigured() {
		when(agentContextService.getContextByUuid(CONTEXT_UUID)).thenReturn(context);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));
		when(agentContextService.resolveContextTargets(9L)).thenReturn(List.of(target(true)));
		when(wordlistRepo.findAllById(Set.of(3L))).thenReturn(List.of());
		AiJobCreationRequest withWordlist = new AiJobCreationRequest(5L, "checks the id parameter", 1L,
				Duration.SHORT, Set.of(3L), null, null, null, null);

		assertThrows(jakarta.persistence.EntityNotFoundException.class,
				() -> service.createDraftAiNarrowJob(CONTEXT_UUID, withWordlist));

		verify(jobRepo, never()).save(any());
		verifyNoInteractions(targetConfigService);
	}

	// ---- requireRoutineAllowedInContext

	@Test
	void allowedRoutinePassesTheContextCheck() {
		service.requireRoutineAllowedInContext(routine, context);
	}

	@Test
	void routineNotInTheContextFailsTheContextCheck() {
		Routine other = RoutineFixtures.usableRoutine();
		other.setId(2L);
		other.setKey("masser");

		assertThrows(IllegalArgumentException.class, () -> service.requireRoutineAllowedInContext(other, context));
	}

	// ---- resolvedTargetOfContext

	@Test
	void resolvedTargetOfTheRequestIsReturned() {
		ResolvedTarget wanted = target(true);
		when(agentContextService.resolveContextTargets(9L)).thenReturn(
				List.of(ResolvedTarget.builder().requestId(6L).autoApproval(false).build(), wanted));

		assertSame(wanted, service.resolvedTargetOfContext(context, 5L));
	}

	@Test
	void requestWithoutAResolvedTargetIsNotFound() {
		when(agentContextService.resolveContextTargets(9L)).thenReturn(List.of(target(true)));

		assertThrows(jakarta.persistence.EntityNotFoundException.class,
				() -> service.resolvedTargetOfContext(context, 77L));
	}

	// ---- cappedRate

	@Test
	void requestedRateIsKeptWhenBelowTheLimits() {
		assertEquals(5, service.cappedRate(5, 50, 20));
	}

	@Test
	void requestedRateIsCappedByTheHostRateLimit() {
		assertEquals(50, service.cappedRate(80, 50, 100));
	}

	@Test
	void requestedRateIsCappedByTheContextMaxRate() {
		assertEquals(20, service.cappedRate(80, 100, 20));
	}

	@Test
	void withoutARequestedRateTheHostRateLimitIsUsed() {
		assertEquals(50, service.cappedRate(null, 50, 100));
	}

	@Test
	void withoutARequestedRateTheContextMaxRateCapsTheHostRateLimit() {
		assertEquals(20, service.cappedRate(null, 50, 20));
	}

	// ---- requireHostAcceptsJobs

	@Test
	void aHostWithARateLimitAcceptsJobs() {
		host.setHostRateLimit(1);

		service.requireHostAcceptsJobs(host);
	}

	@Test
	void aHostWithARateLimitOfZeroDoesNotAcceptJobs() {
		host.setHostRateLimit(0);

		assertThrows(com.automata.job.domain.exception.IneligibleJobTargetException.class,
				() -> service.requireHostAcceptsJobs(host));
	}

}
