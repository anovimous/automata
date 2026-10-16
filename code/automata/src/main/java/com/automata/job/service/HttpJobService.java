package com.automata.job.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.job.api.dto.GenericHttpJobDetailsDto;
import com.automata.job.api.dto.GlobalHttpJobDetailsDto;
import com.automata.job.api.dto.HttpJobFilter;
import com.automata.job.api.dto.NarrowHttpJobDetailsDto;
import com.automata.job.api.dto.WideHttpJobDetailsDto;
import com.automata.job.domain.exception.InvalidJobStateException;
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
import com.automata.job.domain.policy.JobStateTransitions;
import com.automata.job.domain.policy.JobStateTransitions.ToApplyJobStateChange;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
import com.automata.job.domain.valueobject.HttpJobSummaryInternalDto;
import com.automata.job.domain.valueobject.TargetSelectionResult;
import com.automata.job.infra.S3Service;
import com.automata.job.repository.HttpJobRepository;
import com.automata.job.utils.HttpJobUtils;
import com.automata.program.Program;
import com.automata.program.ProgramRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.tenant.Tenant;
import com.automata.tenant.TenantRepository;
import com.automata.wordlist.Wordlist;
import com.automata.wordlist.WordlistRepository;

import io.awspring.cloud.s3.S3Resource;
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

	private final S3Service s3;

	@Transactional(readOnly = true)
	public HttpJob getJob(Long jobId) {

		return jobRepo.findById(jobId).orElseThrow(() -> new EntityNotFoundException("Job not found"));

	}

	@Transactional(readOnly = true)
	public Page<HttpJobSummaryInternalDto> getAllJobs(HttpJobFilter filter, Pageable pageable) {

		Specification<HttpJob> spec = HttpJobUtils.buildSpecification(filter);

		return jobRepo.findAllSummaries(spec, pageable);

	}

	@Transactional(readOnly = true)
	public S3Resource getJobResult(Long jobId) {

		HttpJob job = jobRepo.findById(jobId).orElseThrow(() -> new EntityNotFoundException("Job not found"));

		if (job.getGenericDetails().getCurrentState() != JobState.FINISHED
				&& job.getGenericDetails().getCurrentState() != JobState.FAILED)
			throw new InvalidJobStateException("The job has to end before downloading its result");

		S3Resource result = s3.getJobResult(job.getId());

		if (!result.exists())
			throw new EntityNotFoundException("Result not found");

		return result;

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

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				configFileService.createNarrowConfigFile(fullyConfiguredJob);
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

		WideHttpJob fullyConfiguredJob = switch (targetSelector.getSelectorType()) {

		case SelectorType.MULTIPLE_HOSTS ->
			targetConfigService.configureHostsAsTarget(newlyPersistedJob, selectionResult.hostsIds());

		default -> throw new IllegalArgumentException(
				"Target of this cardinality and type is not allowed to be a target of wide jobs");

		};

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				configFileService.createWideConfigFile(fullyConfiguredJob);
			}
		});

		return fullyConfiguredJob;

	};

	public HttpJob createDraftGlobalJob(GenericHttpJobDetailsDto genericDetails,
			GlobalHttpJobDetailsDto globalJobDetails) {

		throw new UnsupportedOperationException("Global jobs not implemented yet");

	}

	@Transactional
	public void prepareJobForQueueing(Long draftJobId) {

		HttpJob job = jobRepo.findById(draftJobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		applyStateChange(job, JobStateTransitions.prepareForQueueing(job.getGenericDetails().getCurrentState(),
				job.getGenericDetails().getRequestedState()));

		jobRepo.save(job);

	}

	@Transactional
	public void pauseRunningJob(Long runningJobId) {

		HttpJob job = jobRepo.findById(runningJobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		applyStateChange(job, JobStateTransitions.requestPause(job.getGenericDetails().getCurrentState(),
				job.getGenericDetails().getRequestedState()));

		jobRepo.save(job);

	}

	@Transactional
	public void resumePausedJob(Long pausedJobId) {

		HttpJob job = jobRepo.findById(pausedJobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		applyStateChange(job, JobStateTransitions.requestResume(job.getGenericDetails().getCurrentState(),
				job.getGenericDetails().getRequestedState()));

		jobRepo.save(job);

	}

	@Transactional
	public void cancelJob(Long jobId) {

		HttpJob job = jobRepo.findById(jobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		applyStateChange(job, JobStateTransitions.cancel(job.getGenericDetails().getCurrentState(),
				job.getGenericDetails().getRequestedState()));

		jobRepo.save(job);

	}

	@Transactional
	public void updateCurrentJobState(Long jobId, JobState newState, String message) {

		log.info("Update job state request received: jobId={} newState={}", jobId, newState);
		// free text sent by the worker, so it is kept out of the info level
		log.debug("Update job state message: jobId={} message={}", jobId, message);

		HttpJob job = jobRepo.findById(jobId)
				.orElseThrow(() -> new EntityNotFoundException("HttpJob with the given id has not been found"));

		applyStateChange(job, JobStateTransitions.reportStateAppliedByWorker(job.getGenericDetails().getCurrentState(),
				job.getGenericDetails().getRequestedState(), newState));

		jobRepo.save(job);

	}

	private void applyStateChange(HttpJob job, ToApplyJobStateChange change) {
		job.getGenericDetails().setCurrentState(change.toApplyCurrentState());
		job.getGenericDetails().setRequestedState(change.toApplyRequestedState());
	}

	@Transactional
	public void deleteJob(Long jobId) {

		throw new UnsupportedOperationException("Delete operation is not implemented yet");

	}

}
