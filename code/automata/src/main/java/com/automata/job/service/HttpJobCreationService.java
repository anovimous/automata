package com.automata.job.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.automata.agentcontext.AgentContext;
import com.automata.agentcontext.AgentContextService;
import com.automata.agentcontext.common.dto.ResolvedTarget;
import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.job.api.dto.AiJobCreationRequest;
import com.automata.job.api.dto.GenericHttpJobDetailsDto;
import com.automata.job.api.dto.NarrowHttpJobDetailsDto;
import com.automata.job.api.dto.WideHttpJobDetailsDto;
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.utils.HttpJobUtils;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.domain.model.NarrowHttpJob.NarrowHttpJobBuilder;
import com.automata.job.domain.model.WideHttpJob.WideHttpJobBuilder;
import com.automata.job.domain.model.embedded.GenericConfig;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.job.domain.valueobject.TargetSelectionResult;
import com.automata.job.repository.HttpJobRepository;
import com.automata.program.Program;
import com.automata.program.ProgramRepository;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.routine.common.enums.Duration;
import com.automata.routine.common.enums.TargetCardinalityPair;
import com.automata.tenant.Tenant;
import com.automata.tenant.TenantRepository;
import com.automata.wordlist.Wordlist;
import com.automata.wordlist.WordlistRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HttpJobCreationService {

	private final HttpJobRepository jobRepo;

	private final RoutineRepository routineRepo;

	private final ProgramRepository programRepo;

	private final HostRepository hostRepo;

	private final RequestRepository requestRepo;

	private final TenantRepository tenantRepo;

	private final WordlistRepository wordlistRepo;

	private final TargetSelectionService targetSelectionService;

	private final JobTargetConfigService targetConfigService;

	private final JobConfigFileAsyncService configFileService;

	private final CustomConfigValidator customConfigValidator;

	private final AgentContextService agentContextService;

	@Transactional
	public NarrowHttpJob createDraftNarrowJob(GenericHttpJobDetailsDto genericDto, NarrowHttpJobDetailsDto narrowDto) {

		NarrowHttpJobBuilder<?, ?> builder = NarrowHttpJob.builder();

		builder.routine(findRoutineAndValidateCustomConfig(genericDto));

		requireUserRateAndPriority(genericDto.rate(), genericDto.priority());

		builder.genericDetails(draftDetails(genericDto, HttpJobScope.NARROW));

		builder.duration(narrowDto.duration());

		Host host = hostRepo.findById(narrowDto.hostId())
				.orElseThrow(() -> new EntityNotFoundException("Host not found"));

		requireHostAcceptsJobs(host);

		builder.host(host);

		builder.program(host.getProgram());

		Tenant tenant = null;
		if (narrowDto.tenantId() != null)
			tenant = tenantRepo.findById(narrowDto.tenantId())
					.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		builder.tenant(tenant);

		builder.wordlists(findWordlists(genericDto));

		NarrowHttpJob newlyPersistedJob = jobRepo.save(builder.build());

		TargetSelector targetSelector = genericDto.targetSelector();

		TargetSelectionResult selectionResult = targetSelectionService.handleTargetSelector(targetSelector);

		NarrowHttpJob fullyConfiguredJob = switch (selectionResult) {

		case TargetSelectionResult.SingleHost selected -> {
			requireTargetOfJobHost(selected.hostId(), narrowDto.hostId());
			yield targetConfigService.configureHostAsTarget(newlyPersistedJob, selected.hostId());
		}

		case TargetSelectionResult.SingleRequest selected -> {
			requireTargetOfJobHost(selected.hostId(), narrowDto.hostId());
			yield targetConfigService.configureRequestsAsTarget(newlyPersistedJob, Set.of(selected.requestId()));
		}

		case TargetSelectionResult.MultipleRequests selected ->
			targetConfigService.configureRequestsAsTarget(newlyPersistedJob, selected.requestsIds());

		case TargetSelectionResult.MultipleHosts selected -> throw new IllegalArgumentException(
				"Target of this cardinality and type is not allowed to be a target of narrow jobs");

		};

		prepareConfigFilesAfterCommit(fullyConfiguredJob.getId());

		return fullyConfiguredJob;

	}

	@Transactional
	public WideHttpJob createDraftWideJob(GenericHttpJobDetailsDto genericDto, WideHttpJobDetailsDto wideDto) {

		WideHttpJobBuilder<?, ?> builder = WideHttpJob.builder();

		builder.routine(findRoutineAndValidateCustomConfig(genericDto));

		requireUserRateAndPriority(genericDto.rate(), genericDto.priority());

		builder.genericDetails(draftDetails(genericDto, HttpJobScope.WIDE));

		Program program = programRepo.findById(wideDto.programId())
				.orElseThrow(() -> new EntityNotFoundException("Program not found"));

		builder.program(program);

		builder.wordlists(findWordlists(genericDto));

		WideHttpJob newlyPersistedJob = jobRepo.save(builder.build());

		TargetSelector targetSelector = genericDto.targetSelector();

		TargetSelectionResult selectionResult = targetSelectionService.handleTargetSelector(targetSelector);

		WideHttpJob fullyConfiguredJob = switch (selectionResult) {

		case TargetSelectionResult.MultipleHosts selected -> {
			requireHostsAcceptJobs(selected.hostsIds());
			yield targetConfigService.configureHostsAsTarget(newlyPersistedJob, selected.hostsIds());
		}

		default -> throw new IllegalArgumentException(
				"Target of this cardinality and type is not allowed to be a target of wide jobs");

		};

		prepareConfigFilesAfterCommit(fullyConfiguredJob.getId());

		return fullyConfiguredJob;

	};

	@Transactional
	public NarrowHttpJob createDraftAiNarrowJob(UUID agentContextUuid, AiJobCreationRequest aiRequest) {

		requireAiJobFields(aiRequest);

		AgentContext context = agentContextService.getContextByUuid(agentContextUuid);

		Request request = requestRepo.findById(aiRequest.requestId())
				.orElseThrow(() -> new EntityNotFoundException("Request not found"));

		ResolvedTarget target = resolvedTargetOfContext(context, request.getId());

		HttpJobUtils.requireJobEligible(request);

		Host host = request.getHost();

		requireTargetOfJobHost(host.getId(), context.getHost().getId());

		requireHostAcceptsJobs(host);

		Routine routine = routineRepo.findById(aiRequest.routineId())
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

		requireRoutineUsable(routine, SelectorType.SINGLE_REQUEST);

		requireRoutineAllowedInContext(routine, context);

		customConfigValidator.validate(routine, aiRequest.customConfig());

		Duration duration = aiRequest.duration() != null ? aiRequest.duration() : context.getDefaultDuration();
		if (duration == null)
			throw new IllegalArgumentException(
					"duration is required: neither the request nor the agent context gives one");

		ResultsVerbosity verbosity = firstNonNull(aiRequest.verbosity(), context.getDefaultVerbosity(),
				ResultsVerbosity.SUMMARIZED);
		int priority = firstNonNull(aiRequest.priority(), context.getDefaultPriority(), 1);
		requirePriority(priority);
		int rate = cappedRate(aiRequest.rate(), host.getHostRateLimit(), context.getMaxRate());

		TargetSelector targetSelector = new TargetSelector();
		targetSelector.setSelectorType(com.automata.job.domain.model.enums.SelectorType.SINGLE_REQUEST);
		com.automata.job.domain.model.embedded.SingleRequestSelector singleSelector = new com.automata.job.domain.model.embedded.SingleRequestSelector();
		singleSelector.setRequestId(aiRequest.requestId());
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		targetSelector.setSelector(mapper.valueToTree(singleSelector));

		HttpJobGenericDetails genericDetails = HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.NARROW)
				.currentState(JobState.DRAFT).verbosity(verbosity).priority(priority).rate(rate)
				.targetSelector(targetSelector).customConfig(aiRequest.customConfig()).build();

		NarrowHttpJobBuilder<?, ?> builder = NarrowHttpJob.builder();
		builder.routine(routine);
		builder.genericDetails(genericDetails);
		builder.duration(duration);
		builder.host(host);
		builder.program(host.getProgram());
		builder.tenant(request.getTenant());
		builder.createdBy(JobCreator.AI);
		builder.agentContext(context);
		builder.approved(target.autoApproval());
		builder.aiDescription(aiRequest.aiDescription());
		builder.wordlists(findWordlists(aiRequest.wordlistsIds()));

		NarrowHttpJob newlyPersistedJob = jobRepo.save(builder.build());

		TargetSelectionResult selectionResult = targetSelectionService.handleTargetSelector(targetSelector);

		NarrowHttpJob fullyConfiguredJob = switch (selectionResult) {
		case TargetSelectionResult.SingleRequest selected -> {
			requireTargetOfJobHost(selected.hostId(), host.getId());
			yield targetConfigService.configureRequestsAsTarget(newlyPersistedJob, Set.of(selected.requestId()));
		}
		default -> throw new IllegalArgumentException("AI job creation supports only single-request targets");
		};

		prepareConfigFilesAfterCommit(fullyConfiguredJob.getId());

		return fullyConfiguredJob;

	}

	void requireAiJobFields(AiJobCreationRequest aiRequest) {
		if (aiRequest.requestId() == null)
			throw new IllegalArgumentException("requestId is required");
		if (aiRequest.routineId() == null)
			throw new IllegalArgumentException("routineId is required");
		if (aiRequest.aiDescription() == null || aiRequest.aiDescription().isBlank())
			throw new IllegalArgumentException("aiDescription is required");
		if (aiRequest.aiDescription().length() > HttpJob.MAX_AI_DESCRIPTION_LENGTH)
			throw new IllegalArgumentException("aiDescription is too long, the limit is "
					+ HttpJob.MAX_AI_DESCRIPTION_LENGTH + " characters");
		if (aiRequest.wordlistsIds() == null)
			throw new IllegalArgumentException("wordlistsIds is required, send an empty set for none");
		requireRateAndPriority(aiRequest.rate(), aiRequest.priority());
	}

	void requireRoutineAllowedInContext(Routine routine, AgentContext context) {
		boolean allowed = context.getAllowedRoutines().stream().anyMatch(r -> r.getId().equals(routine.getId()));
		if (!allowed)
			throw new IllegalArgumentException("Routine '" + routine.getKey() + "' is not allowed in this agent context");
	}

	// A job can only target a request that the context gives access to (a resolved
	// target); its auto-approval is the one of the item that gave the access.
	ResolvedTarget resolvedTargetOfContext(AgentContext context, Long requestId) {
		return agentContextService.resolveContextTargets(context.getId()).stream()
				.filter(target -> target.requestId().equals(requestId)).findFirst()
				.orElseThrow(() -> new EntityNotFoundException("Request not found in the agent context"));
	}

	// the rate is the requested one, or the host rate limit when none is requested, and it is
	// capped by the host rate limit and the context max rate (both are at least 1 here)
	int cappedRate(Integer requested, int hostRateLimit, int contextMaxRate) {
		int rate = requested != null ? requested : hostRateLimit;
		return Math.min(rate, Math.min(hostRateLimit, contextMaxRate));
	}

	private static <T> T firstNonNull(T first, T second, T fallback) {
		return first != null ? first : second != null ? second : fallback;
	}

	Routine findRoutineAndValidateCustomConfig(GenericHttpJobDetailsDto genericDto) {
		Routine routine = findRoutine(genericDto);
		if (genericDto.targetSelector() != null)
			requireRoutineUsable(routine, genericDto.targetSelector().getSelectorType());
		customConfigValidator.validate(routine, genericDto.customConfig());
		return routine;
	}

	// The routine must be one the worker can run and it must allow the kind of target of the job
	void requireRoutineUsable(Routine routine, SelectorType selectorType) {
		if (!routine.isAvailableAtConsumer())
			throw new IllegalArgumentException("Routine '" + routine.getKey() + "' is not available at the worker");
		if (selectorType == null)
			return;
		TargetCardinalityPair target = switch (selectorType) {
		case SINGLE_HOST -> TargetCardinalityPair.SINGLE_HOST;
		case MULTIPLE_HOSTS -> TargetCardinalityPair.MULTILPLE_HOSTS;
		case SINGLE_REQUEST -> TargetCardinalityPair.SINGLE_REQUEST;
		case MULTIPLE_REQUESTS -> TargetCardinalityPair.MULTIPLE_REQUESTS;
		};
		if (routine.getAllowedTargets() == null || !routine.getAllowedTargets().contains(target))
			throw new IllegalArgumentException(
					"Routine '" + routine.getKey() + "' does not allow " + selectorType + " targets");
	}

	// The rate and the priority a user gives: a job always has a rate, so it is required
	void requireUserRateAndPriority(Integer rate, Integer priority) {
		if (rate == null)
			throw new IllegalArgumentException("rate is required");
		requireRateAndPriority(rate, priority);
	}

	// Only a value that is given is checked: a missing one takes its default
	void requireRateAndPriority(Integer rate, Integer priority) {
		HttpJobUtils.requireValidRate(rate, "rate");
		HttpJobUtils.requireValidPriority(priority, "priority");
	}

	void requirePriority(int priority) {
		HttpJobUtils.requireValidPriority(priority, "priority");
	}

	Routine findRoutine(GenericHttpJobDetailsDto genericDto) {
		return routineRepo.findById(genericDto.routineId())
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));
	}

	HttpJobGenericDetails draftDetails(GenericHttpJobDetailsDto genericDto, HttpJobScope scope) {
		return HttpJobGenericDetails.builder().httpJobScope(scope).currentState(JobState.DRAFT)
				.verbosity(genericDto.verbosity()).priority(genericDto.priority()).rate(genericDto.rate())
				.targetSelector(genericDto.targetSelector()).genericConfig(GenericConfig.of(genericDto.matchAndReplace()))
				.customConfig(genericDto.customConfig()).build();
	}

	List<Wordlist> findWordlists(GenericHttpJobDetailsDto genericDto) {
		return findWordlists(genericDto.wordlistsIds());
	}

	// Every given id must be a wordlist, an unknown one is not dropped silently
	List<Wordlist> findWordlists(Set<Long> wordlistIds) {
		if (wordlistIds == null || wordlistIds.isEmpty())
			return new ArrayList<>();
		List<Wordlist> wordlists = wordlistRepo.findAllById(wordlistIds);
		if (wordlists.size() != wordlistIds.size()) {
			Set<Long> missing = new TreeSet<>(wordlistIds);
			wordlists.forEach(wordlist -> missing.remove(wordlist.getId()));
			throw new EntityNotFoundException("Wordlist not found: " + missing);
		}
		return wordlists;
	}

	void prepareConfigFilesAfterCommit(Long jobId) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				configFileService.prepareConfigFiles(jobId);
			}
		});
	}

	// A host with a rate limit of 0 is closed to jobs: the scheduler could never run one
	void requireHostAcceptsJobs(Host host) {
		if (host.getHostRateLimit() < 1)
			throw IneligibleJobTargetException.forHostWithoutRateLimit(host.getId());
	}

	void requireHostsAcceptJobs(Set<Long> hostIds) {
		hostRepo.findDtosByIds(hostIds).stream().filter(dto -> dto.rateLimit() < 1).findFirst().ifPresent(dto -> {
			throw IneligibleJobTargetException.forHostWithoutRateLimit(dto.hostId());
		});
	}

	// a single host or request target must belong to the host the job (and its rate
	// limits) is for
	private void requireTargetOfJobHost(Long targetHostId, Long jobHostId) {
		if (!jobHostId.equals(targetHostId))
			throw IneligibleJobTargetException.forOtherHost(targetHostId, jobHostId);
	}

}
