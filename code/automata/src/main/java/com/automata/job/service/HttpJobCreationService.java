package com.automata.job.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

	@Transactional
	public NarrowHttpJob createDraftNarrowJob(GenericHttpJobDetailsDto genericDto, NarrowHttpJobDetailsDto narrowDto) {

		NarrowHttpJobBuilder<?, ?> builder = NarrowHttpJob.builder();

		builder.routine(findRoutine(genericDto));

		builder.genericDetails(draftDetails(genericDto, HttpJobScope.NARROW));

		builder.duration(narrowDto.duration());

		Host host = hostRepo.findById(narrowDto.hostId())
				.orElseThrow(() -> new EntityNotFoundException("Host not found"));

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

		builder.routine(findRoutine(genericDto));

		builder.genericDetails(draftDetails(genericDto, HttpJobScope.WIDE));

		Program program = programRepo.findById(wideDto.programId())
				.orElseThrow(() -> new EntityNotFoundException("Program not found"));

		builder.program(program);

		builder.wordlists(findWordlists(genericDto));

		WideHttpJob newlyPersistedJob = jobRepo.save(builder.build());

		TargetSelector targetSelector = genericDto.targetSelector();

		TargetSelectionResult selectionResult = targetSelectionService.handleTargetSelector(targetSelector);

		WideHttpJob fullyConfiguredJob = switch (selectionResult) {

		case TargetSelectionResult.MultipleHosts selected ->
			targetConfigService.configureHostsAsTarget(newlyPersistedJob, selected.hostsIds());

		default -> throw new IllegalArgumentException(
				"Target of this cardinality and type is not allowed to be a target of wide jobs");

		};

		prepareConfigFilesAfterCommit(fullyConfiguredJob.getId());

		return fullyConfiguredJob;

	};

	@Transactional
	public NarrowHttpJob createDraftAiNarrowJob(AiJobCreationRequest aiRequest) {

		Request request = requestRepo.findById(aiRequest.requestId())
				.orElseThrow(() -> new EntityNotFoundException("Request not found"));

		HttpJobUtils.requireJobEligible(request);

		Routine routine = routineRepo.findById(aiRequest.routineId())
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

		Host host = request.getHost();

		int rate = aiRequest.rate() != null ? aiRequest.rate() : host.getHostRateLimit();
		if (host.getHostRateLimit() > 0 && rate > host.getHostRateLimit())
			rate = host.getHostRateLimit();

		ResultsVerbosity verbosity = aiRequest.verbosity() != null ? aiRequest.verbosity() : ResultsVerbosity.VERBOSE;
		int priority = aiRequest.priority() != null ? aiRequest.priority() : 1;

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
		builder.duration(aiRequest.duration());
		builder.host(host);
		builder.program(host.getProgram());
		builder.tenant(request.getTenant());
		builder.createdBy(JobCreator.AI);
		builder.approved(false);
		builder.aiDescription(aiRequest.aiDescription());

		if (aiRequest.wordlistsIds() != null && !aiRequest.wordlistsIds().isEmpty())
			builder.wordlists(wordlistRepo.findAllById(aiRequest.wordlistsIds()));

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
		if (genericDto.wordlistsIds() == null)
			return new ArrayList<>();
		return wordlistRepo.findAllById(genericDto.wordlistsIds());
	}

	void prepareConfigFilesAfterCommit(Long jobId) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				configFileService.prepareConfigFiles(jobId);
			}
		});
	}

	// a single host or request target must belong to the host the job (and its rate
	// limits) is for
	private void requireTargetOfJobHost(Long targetHostId, Long jobHostId) {
		if (!jobHostId.equals(targetHostId))
			throw IneligibleJobTargetException.forOtherHost(targetHostId, jobHostId);
	}

}
