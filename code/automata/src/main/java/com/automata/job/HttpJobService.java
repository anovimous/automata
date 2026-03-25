package com.automata.job;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.job.NarrowHttpJob.NarrowHttpJobBuilder;
import com.automata.job.WideHttpJob.WideHttpJobBuilder;
import com.automata.job.common.dto.GenericHttpJobDetailsDto;
import com.automata.job.common.dto.GlobalHttpJobDetailsDto;
import com.automata.job.common.dto.NarrowHttpJobDetailsDto;
import com.automata.job.common.dto.WideHttpJobDetailsDto;
import com.automata.job.common.enums.HttpJobScope;
import com.automata.job.common.enums.JobState;
import com.automata.job.selector.TargetSelector;
import com.automata.job.selector.result.TargetSelectionResult;
import com.automata.job.common.enums.SelectorType;
import com.automata.program.Program;
import com.automata.program.ProgramRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.tenant.Tenant;
import com.automata.tenant.TenantRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HttpJobService {

	private final HttpJobRepository jobRepo;

	private final RoutineRepository routineRepo;

	private final ProgramRepository programRepo;

	private final HostRepository hostRepo;

	private final TenantRepository tenantRepo;

	private final TargetSelectionService targetSelectionService;

	private final JobTargetConfigService targetConfigService;

	private final JobConfigFileService configFileService;

	public NarrowHttpJob createDraftNarrowJob(GenericHttpJobDetailsDto genericDto, NarrowHttpJobDetailsDto narrowDto) {

		NarrowHttpJobBuilder<?, ?> builder = NarrowHttpJob.builder();

		Routine routine = routineRepo.findById(genericDto.routineId())
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

		builder.routine(routine);

		HttpJobGenericDetails genericDetails = HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.NARROW)
				.state(JobState.DRAFT).verbosity(genericDto.verbosity()).priority(genericDto.priority())
				.rate(genericDto.rate()).targetSelector(genericDto.targetSelector())
				.genericConfig(GenericConfig.of(genericDto.matchAndReplace())).customConfig(genericDto.customConfig())
				.build();

		builder.genericDetails(genericDetails);

		builder.duration(narrowDto.duration());

		Host host = hostRepo.findById(narrowDto.hostId())
				.orElseThrow(() -> new EntityNotFoundException("Host not found"));

		builder.host(host);

		builder.program(host.getProgram());

		Tenant tenant = tenantRepo.findById(narrowDto.tenantId())
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		builder.tenant(tenant);

		NarrowHttpJob newlyPersistedJob = jobRepo.save(builder.build());

		TargetSelector targetSelector = genericDto.targetSelector();

		TargetSelectionResult selectionResult = targetSelectionService.handleTargetSelector(targetSelector);

		NarrowHttpJob fullyConfiguredJob = switch (targetSelector.getSelectorType()) {

		case SelectorType.SINGLE_HOST ->
			targetConfigService.configureHostAsTarget(newlyPersistedJob, selectionResult.hostId());

		case SelectorType.SINGLE_REQUEST ->
			targetConfigService.configureRequestsAsTarget(newlyPersistedJob, Set.of(selectionResult.requestId()));

		case SelectorType.MULTIPLE_REQUESTS ->
			targetConfigService.configureRequestsAsTarget(newlyPersistedJob, selectionResult.requestsIds());

		default -> throw new IllegalArgumentException(
				"Target of this cardinality and type is not allowed to be a target of narrow jobs");

		};

		configFileService.createNarrowConfigFile(fullyConfiguredJob);

		return fullyConfiguredJob;

	}

	public WideHttpJob createDraftWideJob(GenericHttpJobDetailsDto genericDto, WideHttpJobDetailsDto wideDto) {

		WideHttpJobBuilder<?, ?> builder = WideHttpJob.builder();

		Routine routine = routineRepo.findById(genericDto.routineId())
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

		builder.routine(routine);

		HttpJobGenericDetails genericDetails = HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.NARROW)
				.state(JobState.DRAFT).verbosity(genericDto.verbosity()).priority(genericDto.priority())
				.rate(genericDto.rate()).targetSelector(genericDto.targetSelector())
				.genericConfig(GenericConfig.of(genericDto.matchAndReplace())).customConfig(genericDto.customConfig())
				.build();

		builder.genericDetails(genericDetails);

		Program program = programRepo.findById(wideDto.programId())
				.orElseThrow(() -> new EntityNotFoundException("Program not found"));

		builder.program(program);

		WideHttpJob newlyPersistedJob = jobRepo.save(builder.build());

		TargetSelector targetSelector = genericDto.targetSelector();

		TargetSelectionResult selectionResult = targetSelectionService.handleTargetSelector(targetSelector);

		WideHttpJob fullyConfiguredJob = switch (targetSelector.getSelectorType()) {

		case SelectorType.MULTIPLE_HOSTS ->
			targetConfigService.configureHostsAsTarget(newlyPersistedJob, selectionResult.hostsIds());

		default -> throw new IllegalArgumentException(
				"Target of this cardinality and type is not allowed to be a target of wide jobs");

		};

		configFileService.createWideConfigFile(fullyConfiguredJob);

		return fullyConfiguredJob;

	};


	public HttpJob createDraftGlobalJob(GenericHttpJobDetailsDto genericDetails,
			GlobalHttpJobDetailsDto globalJobDetails) {

		throw new RuntimeException("Global jobs not implemented yet");

	}

}
