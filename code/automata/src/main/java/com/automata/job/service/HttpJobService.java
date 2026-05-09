package com.automata.job.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.job.api.dto.GenericHttpJobDetailsDto;
import com.automata.job.api.dto.GlobalHttpJobDetailsDto;
import com.automata.job.api.dto.NarrowHttpJobDetailsDto;
import com.automata.job.api.dto.WideHttpJobDetailsDto;
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
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
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

	private final JobConfigFileService configFileService;

	@Transactional(readOnly = true)
	public HttpJob getJob(Long jobId) {

		return jobRepo.findById(jobId).orElseThrow(() -> new EntityNotFoundException("Job not found"));

	}

	@Transactional(readOnly = true)
	public HttpJobFullDetailsInternalDto getJobFullDetails(Long jobId) {

		HttpJobFullDetailsInternalDto jobDto = jobRepo.findFullDetailsByJobId(jobId)
				.orElseThrow(() -> new EntityNotFoundException("Job not found"));

		return jobDto;

	}

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

		Tenant tenant = tenantRepo.findById(narrowDto.tenantId())
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		builder.tenant(tenant);

		List<Wordlist> wordlists = new ArrayList<>();
		if (genericDto.wordlistsIds() != null)
			wordlists = wordlistRepo.findAllById(genericDto.wordlistsIds());

		builder.wordlists(wordlists);

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

	@Transactional
	public WideHttpJob createDraftWideJob(GenericHttpJobDetailsDto genericDto, WideHttpJobDetailsDto wideDto) {

		WideHttpJobBuilder<?, ?> builder = WideHttpJob.builder();

		Routine routine = routineRepo.findById(genericDto.routineId())
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

		builder.routine(routine);

		HttpJobGenericDetails genericDetails = HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.NARROW)
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

	@Transactional
	public void prepareJobForQueueing(Long draftJobId) {

		HttpJob job = jobRepo.findById(draftJobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		if (job.getGenericDetails().getCurrentState() != JobState.DRAFT)
			throw new RuntimeException("The job must be in the DRAFT state in order to prepare it for queueing");

		job.getGenericDetails().setCurrentState(JobState.TOQUEUE);

		jobRepo.save(job);

	}

	@Transactional
	public void pauseRunningJob(Long runningJobId) {

		HttpJob job = jobRepo.findById(runningJobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		if (job.getGenericDetails().getCurrentState() != JobState.RUNNING)
			throw new RuntimeException("The job must be in the RUNNING state in order to pause it");

		job.getGenericDetails().setRequestedState(JobState.PAUSED);

		jobRepo.save(job);

	}

	@Transactional
	public void resumePausedJob(Long pausedJobId) {

		HttpJob job = jobRepo.findById(pausedJobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		if (job.getGenericDetails().getCurrentState() != JobState.PAUSED)
			throw new RuntimeException("The job must be in the PAUSED state in order to resume it");

		job.getGenericDetails().setRequestedState(JobState.RUNNING);

		jobRepo.save(job);

	}

	@Transactional
	public void cancelJob(Long jobId) {

		HttpJob job = jobRepo.findById(jobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		if (job.getGenericDetails().getCurrentState() == JobState.SCHEDULED)
			throw new RuntimeException("Canceling scheduled jobs not implemented is yet");

		if (!List.of(JobState.TOQUEUE, JobState.SCHEDULED, JobState.QUEUED, JobState.PAUSED)
				.contains(job.getGenericDetails().getCurrentState()))
			throw new RuntimeException(
					"The job must be in the [TOQUEUE,SCHEDULED,QUEUED,PAUSED] states in order to cancel it");

		job.getGenericDetails().setRequestedState(JobState.CANCELED);

		jobRepo.save(job);

	}

	@Transactional
	public void updateCurrentJobState(Long jobId, JobState newState, String message) {

		log.info("Update job state request received: jobId=" + jobId + " ,newState=" + newState + " ,message="
				+ message);

		HttpJob job = jobRepo.findById(jobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		if (List.of(JobState.DRAFT, JobState.TOQUEUE, JobState.SCHEDULED, JobState.QUEUED).contains(newState))
			throw new RuntimeException("The job is not allowed to this state by the orchestrator");

		job.getGenericDetails().setCurrentState(newState);

		job.getGenericDetails().setRequestedState(null);

		jobRepo.save(job);

	}

}
