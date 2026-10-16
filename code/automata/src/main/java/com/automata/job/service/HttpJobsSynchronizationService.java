package com.automata.job.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.policy.JobDispatchOrder;
import com.automata.job.domain.valueobject.HttpJobInternalDto;
import com.automata.job.domain.valueobject.HttpJobRoutineInternalDto;
import com.automata.job.domain.valueobject.ProgramCurrentWideRateDto;
import com.automata.job.domain.valueobject.ProgramRateDto;
import com.automata.job.domain.valueobject.ProgramSummaryRatesDto;
import com.automata.job.infra.JobQueueService;
import com.automata.job.repository.HttpJobRepository;
import com.automata.job.repository.NarrowHttpJobRepository;
import com.automata.job.repository.WideHttpJobRepository;
import com.automata.program.ProgramRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class HttpJobsSynchronizationService {

	private final HttpJobRepository jobRepo;

	private final NarrowHttpJobRepository narrowJobRepo;

	private final WideHttpJobRepository wideJobRepo;

	private final ProgramRepository programRepo;

	private final JobDispatchPlanner dispatchPlanner;

	private final JobQueueService queueService;

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public void syncToQueueJobs() {

		Deque<HttpJobInternalDto> sortedToQueueJobs = this.getSortedToQueueJobs();
		
		Map<Long, String> jobsRoutineKeysMap = jobRepo
				.findJobsRoutineKeys(sortedToQueueJobs.stream().map(HttpJobInternalDto::id).toList()).stream()
				.collect(Collectors.toMap(HttpJobRoutineInternalDto::id, HttpJobRoutineInternalDto::routineKey));

		Set<Long> programsIds = sortedToQueueJobs.stream().map(dto -> dto.programId()).collect(Collectors.toSet());
		Map<Long, ProgramSummaryRatesDto> programSummaryRatesDtosMap = new HashMap<>(
				this.getSummaryOfRates(programsIds));

		List<HttpJobInternalDto> acceptedJobs = dispatchPlanner.plan(sortedToQueueJobs, programSummaryRatesDtosMap);

		acceptedJobs.forEach(job -> queueService.queueHttpJob(job, jobsRoutineKeysMap.get(job.id())));

		jobRepo.updateJobsState(acceptedJobs.stream().map(HttpJobInternalDto::id).collect(Collectors.toSet()),
				JobState.QUEUED, JobState.TOQUEUE);

	}

	private Deque<HttpJobInternalDto> getSortedToQueueJobs() {

		List<HttpJobInternalDto> toQueueJobs = new ArrayList<>();

		toQueueJobs.addAll(narrowJobRepo.getJobsDtosByCurrentState(JobState.TOQUEUE));

		toQueueJobs.addAll(wideJobRepo.getJobsDtosByCurrentState(JobState.TOQUEUE));

		return new ArrayDeque<>(JobDispatchOrder.sort(toQueueJobs));
	}

	private Map<Long, ProgramSummaryRatesDto> getSummaryOfRates(Set<Long> programsIds) {

		List<ProgramRateDto> programRateDtos = jobRepo.getProgramsRateDtos(programsIds,
				Set.of(JobState.QUEUED, JobState.RUNNING, JobState.PAUSED));

		List<ProgramCurrentWideRateDto> programWideCurrentRateDtos = wideJobRepo.getProgramsWideRateDtos(programsIds,
				Set.of(JobState.QUEUED, JobState.RUNNING, JobState.PAUSED));

		Map<Long, Long> programCurrentWideRateMap = programWideCurrentRateDtos.stream().collect(
				Collectors.toMap(ProgramCurrentWideRateDto::programId, ProgramCurrentWideRateDto::currentWideRate));

		Map<Long, ProgramSummaryRatesDto> programSummaryRatesDtosMap = programRateDtos.stream()
				.collect(Collectors.toMap(ProgramRateDto::programId,
						dto -> ProgramSummaryRatesDto.builder().rateLimit(dto.rateLimit())
								.currentRate(dto.currentRate())
								.currentWideRate(programCurrentWideRateMap.get(dto.programId())).build()));

		return programSummaryRatesDtosMap;

	}

}
