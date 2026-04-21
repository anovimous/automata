package com.automata.job.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.automata.host.HostRepository;
import com.automata.host.common.dto.HostAllRateLimitsInternalDto;
import com.automata.host.common.dto.HostRateLimitInternalDto;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobInternalDto;
import com.automata.job.domain.valueobject.NarrowHttpJobInternalDto;
import com.automata.job.domain.valueobject.NarrowHttpJobRateInternalDto;
import com.automata.job.domain.valueobject.ProgramCurrentWideRateDto;
import com.automata.job.domain.valueobject.ProgramRateDto;
import com.automata.job.domain.valueobject.ProgramSummaryRatesDto;
import com.automata.job.repository.HttpJobRepository;
import com.automata.job.repository.NarrowHttpJobRepository;
import com.automata.job.repository.WideHttpJobRepository;
import com.automata.program.ProgramRepository;
import com.automata.routine.common.enums.Duration;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HttpJobsSynchronizationService {

	private final HttpJobRepository jobRepo;

	private final NarrowHttpJobRepository narrowJobRepo;

	private final WideHttpJobRepository wideJobRepo;

	private final ProgramRepository programRepo;

	private final HostRepository hostRepo;

	private final JobQueueService queueService;

	public void syncToQueueJobs() {

		TreeSet<HttpJobInternalDto> sortedToQueueJobs = this.getSortedToQueueJobs();

		Set<Long> programsIds = sortedToQueueJobs.stream().map(dto -> dto.programId()).collect(Collectors.toSet());

		Map<Long, ProgramSummaryRatesDto> programSummaryRatesDtosMap = this.getSummaryOfRates(programsIds);

		while (!sortedToQueueJobs.isEmpty()) {

			HttpJobInternalDto firstJob = sortedToQueueJobs.pollFirst();

			ProgramSummaryRatesDto programRate = programSummaryRatesDtosMap.get(firstJob.programId());

			boolean abidesToProgramRateLimit = programRate.currentRate() + firstJob.rate() <= programRate.rateLimit();

			Boolean wideJobAbidesToEachHostRateLimit = false, narrowJobAbidesToTargetHostRateLimit = false;

			if (firstJob.scope() == HttpJobScope.WIDE) {

				if (abidesToProgramRateLimit)
					wideJobAbidesToEachHostRateLimit = checkAbidesToEachHostRateLimit(firstJob, programRate);
				else
					sortedToQueueJobs.removeIf((job) -> job.scope().equals(HttpJobScope.WIDE)
							&& job.programId().equals(firstJob.programId()));

			} else {

				if (abidesToProgramRateLimit)
					narrowJobAbidesToTargetHostRateLimit = checkAbidesToHostRateLimit(firstJob, programRate);
				else
					sortedToQueueJobs.removeIf((job) -> job.scope().equals(HttpJobScope.NARROW)
							&& job.hostId().equals(firstJob.hostId()) && job.duration().equals(firstJob.duration()));

			}

			if (abidesToProgramRateLimit && (wideJobAbidesToEachHostRateLimit || narrowJobAbidesToTargetHostRateLimit))
				queueService.queueHttpJob(firstJob);
		}
	}

	// DONE:
	private boolean checkAbidesToEachHostRateLimit(HttpJobInternalDto jobDto, ProgramSummaryRatesDto programRate) {

		// 1. first check if there is any host that has a max rate limit lesser than the
		// (new job X rate + currentWideRate), if any then remove the job and mark as
		// forbidden
		// 2. get the current total rate of narrow routines in each host in the program,
		// for those that have nothing running in them don't return(returning them is
		// useless, we already checked if their max rate limit is lesser than the (new
		// job rate + currentWideRate) and there is no jobs running in them, so for sure
		// the new job abides to the limit of each of these)
		// 3. get the total rate of wide jobs for the program
		// 4. check if abides for each host

		// LATER IMPROVEMENT: check only for target hosts of a job not all program hosts

		if (hostRepo.anyInScopeProgramHostHasRateLimitLessThanGivenRate(jobDto.programId(), jobDto.rate()))
			return false;

		List<NarrowHttpJobRateInternalDto> narrowJobDtos = narrowJobRepo.findAllByProgramIdAndCurrentStateIn(
				jobDto.programId(), List.of(JobState.QUEUED, JobState.RUNNING, JobState.PAUSED));

		// group the jobs by host and then sum the narrow jobs in the same host

		Map<Long, Integer> hostsCurrentNarrowRateMap = new HashMap<>();

		narrowJobDtos.forEach((dto) -> {
			Long hostId = dto.hostId();
			if (hostsCurrentNarrowRateMap.containsKey(hostId))
				hostsCurrentNarrowRateMap.put(hostId, hostsCurrentNarrowRateMap.get(hostId) + dto.rate());
			else
				hostsCurrentNarrowRateMap.put(hostId, dto.rate());
		});

		// get rate limit for each host
		List<HostRateLimitInternalDto> hostsDtos = hostRepo.findDtosByIds(hostsCurrentNarrowRateMap.keySet());

		Map<Long, Integer> hostsRateLimitMap = hostsDtos.stream()
				.collect(Collectors.toMap(HostRateLimitInternalDto::hostId, HostRateLimitInternalDto::rateLimit));

		for (Map.Entry<Long, Integer> entry : hostsCurrentNarrowRateMap.entrySet()) {

			// check for each of these hosts H if ((new job X rate + current Narrow rate of
			// H + current Wide rate of Program of H <= H rate limit))

			long newCurrentTotalRateInHost = jobDto.rate() + entry.getValue() + programRate.currentWideRate();

			if (newCurrentTotalRateInHost > hostsRateLimitMap.get(entry.getKey()))
				return false;

		}

		return true;

	}

	// DONE:
	private boolean checkAbidesToHostRateLimit(HttpJobInternalDto jobDto, ProgramSummaryRatesDto programRate) {

		// check abides to host rate limit:
		// ((new job X rate + current Narrow rate of H + current Wide rate of Program of
		// H <= H rate limit))

		// if long check for long rate limit
		// if short check for short rate limit

		HostAllRateLimitsInternalDto hostDto = hostRepo.findRateLimitsAllDtoById(jobDto.hostId());

		List<NarrowHttpJobInternalDto> narrowJobsDtos = narrowJobRepo.findAllByHostIdAndStateIn(jobDto.hostId(),
				List.of(JobState.QUEUED, JobState.RUNNING, JobState.PAUSED));

		Integer currentNarrowRate = 0, currentShortRate = 0, currentLongRate = 0;

		for (NarrowHttpJobInternalDto narrowJobDto : narrowJobsDtos) {
			currentNarrowRate += narrowJobDto.rate();
			if (narrowJobDto.duration() == Duration.SHORT)
				currentShortRate += narrowJobDto.rate();
			else
				currentLongRate += narrowJobDto.rate();
		}

		long newCurrentRateInHost = jobDto.rate() + currentNarrowRate + programRate.currentWideRate();

		if (newCurrentRateInHost > hostDto.rateLimit())
			return false;

		if (jobDto.duration() == Duration.SHORT)
			return jobDto.rate() + currentShortRate <= hostDto.shortRateLimit();
		else
			return jobDto.rate() + currentLongRate <= hostDto.longRateLimit();

	}

	// TO MOVE into their services:

	private TreeSet<HttpJobInternalDto> getSortedToQueueJobs() {

		TreeSet<HttpJobInternalDto> sortedToQueueJobs = new TreeSet<>();

		sortedToQueueJobs.addAll(narrowJobRepo.getJobsDtosByCurrentState(JobState.TOQUEUE));

		sortedToQueueJobs.addAll(wideJobRepo.getJobsDtosByCurrentState(JobState.TOQUEUE));

		return sortedToQueueJobs;
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
