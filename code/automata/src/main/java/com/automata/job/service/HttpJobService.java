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
import com.automata.job.api.dto.GenericHttpJobDetailsDto;
import com.automata.job.api.dto.GlobalHttpJobDetailsDto;
import com.automata.job.api.dto.NarrowHttpJobDetailsDto;
import com.automata.job.api.dto.WideHttpJobDetailsDto;
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.NarrowHttpJob.NarrowHttpJobBuilder;
import com.automata.job.domain.model.WideHttpJob.WideHttpJobBuilder;
import com.automata.job.domain.model.embedded.GenericConfig;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.TargetSelectionResult;
import com.automata.job.repository.HttpJobRepository;
import com.automata.program.Program;
import com.automata.program.ProgramRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.tenant.Tenant;
import com.automata.tenant.TenantRepository;
import com.automata.wordlist.Wordlist;
import com.automata.wordlist.WordlistRepository;


import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class HttpJobService {

	private final HttpJobRepository jobRepo;

	private final RoutineRepository routineRepo;

	private final ProgramRepository programRepo;

	private final HostRepository hostRepo;

	private final TenantRepository tenantRepo;

	private final WordlistRepository wordlistRepo;

	private final TargetSelectionService targetSelectionService;

	private final JobTargetConfigService targetConfigService;

	private final JobConfigFileAsyncService configFileService;

	@Transactional
	public NarrowHttpJob createDraftNarrowJob(GenericHttpJobDetailsDto genericDto, NarrowHttpJobDetailsDto narrowDto) {

		NarrowHttpJobBuilder<?, ?> builder = NarrowHttpJob.builder();

		Routine routine = routineRepo.findById(genericDto.routineId())
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

		builder.routine(routine);

		HttpJobGenericDetails genericDetails = HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.NARROW)
				.currentState(JobState.DRAFT).verbosity(genericDto.verbosity()).priority(genericDto.priority())
				.rate(genericDto.rate()).targetSelector(genericDto.targetSelector())
				.genericConfig(GenericConfig.of(genericDto.matchAndReplace())).customConfig(genericDto.customConfig())
				.build();

		builder.genericDetails(genericDetails);

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

		List<Wordlist> wordlists = new ArrayList<>();
		if (genericDto.wordlistsIds() != null)
			wordlists = wordlistRepo.findAllById(genericDto.wordlistsIds());

		builder.wordlists(wordlists);

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

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				configFileService.prepareConfigFiles(fullyConfiguredJob.getId());
			}
		});

		return fullyConfiguredJob;

	}

	@Transactional
	public WideHttpJob createDraftWideJob(GenericHttpJobDetailsDto genericDto, WideHttpJobDetailsDto wideDto) {

		WideHttpJobBuilder<?, ?> builder = WideHttpJob.builder();

		Routine routine = routineRepo.findById(genericDto.routineId())
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

		builder.routine(routine);

		HttpJobGenericDetails genericDetails = HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.WIDE)
				.currentState(JobState.DRAFT).verbosity(genericDto.verbosity()).priority(genericDto.priority())
				.rate(genericDto.rate()).targetSelector(genericDto.targetSelector())
				.genericConfig(GenericConfig.of(genericDto.matchAndReplace())).customConfig(genericDto.customConfig())
				.build();

		builder.genericDetails(genericDetails);

		Program program = programRepo.findById(wideDto.programId())
				.orElseThrow(() -> new EntityNotFoundException("Program not found"));

		builder.program(program);

		List<Wordlist> wordlists = new ArrayList<>();
		if (genericDto.wordlistsIds() != null)
			wordlists = wordlistRepo.findAllById(genericDto.wordlistsIds());

		builder.wordlists(wordlists);

		WideHttpJob newlyPersistedJob = jobRepo.save(builder.build());

		TargetSelector targetSelector = genericDto.targetSelector();

		TargetSelectionResult selectionResult = targetSelectionService.handleTargetSelector(targetSelector);

		WideHttpJob fullyConfiguredJob = switch (selectionResult) {

		case TargetSelectionResult.MultipleHosts selected ->
			targetConfigService.configureHostsAsTarget(newlyPersistedJob, selected.hostsIds());

		default -> throw new IllegalArgumentException(
				"Target of this cardinality and type is not allowed to be a target of wide jobs");

		};

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				configFileService.prepareConfigFiles(fullyConfiguredJob.getId());
			}
		});

		return fullyConfiguredJob;

	};

	public HttpJob createDraftGlobalJob(GenericHttpJobDetailsDto genericDetails,
			GlobalHttpJobDetailsDto globalJobDetails) {

		throw new UnsupportedOperationException("Global jobs not implemented yet");

	}

	// a single host or request target must belong to the host the job (and its rate
	// limits) is for
	private void requireTargetOfJobHost(Long targetHostId, Long jobHostId) {
		if (!jobHostId.equals(targetHostId))
			throw IneligibleJobTargetException.forOtherHost(targetHostId, jobHostId);
	}

}
