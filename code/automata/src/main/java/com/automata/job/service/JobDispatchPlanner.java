package com.automata.job.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.automata.job.domain.valueobject.ProgramSummaryRatesDto;
import com.automata.job.repository.NarrowHttpJobRepository;
import com.automata.routine.common.enums.Duration;

import lombok.RequiredArgsConstructor;

/**
 * Decides which of the sorted candidate jobs fit the program and host rate
 * limits. It only decides: loading the candidates and the program rates, and
 * queueing and persisting the accepted jobs, is up to the caller.
 */
@Service
@RequiredArgsConstructor
public class JobDispatchPlanner {

	private final NarrowHttpJobRepository narrowJobRepo;

	private final HostRepository hostRepo;

	/**
	 * @param sortedToQueueJobs the candidates in the order they are handled
	 * @param programRates      the rates of each program, as a snapshot of the
	 *                          database
	 * @return the accepted jobs, in the order they were accepted
	 */
	public List<HttpJobInternalDto> plan(Collection<HttpJobInternalDto> sortedToQueueJobs,
			Map<Long, ProgramSummaryRatesDto> programRates) {

		Deque<HttpJobInternalDto> sortedToQueueJobsDeque = new ArrayDeque<>(sortedToQueueJobs);
		Map<Long, ProgramSummaryRatesDto> programSummaryRatesDtosMap = new HashMap<>(programRates);
		List<HttpJobInternalDto> acceptedJobs = new ArrayList<>();

		// The rates above are a snapshot of the database, which only learns about the
		// jobs queued in this run after the loop. So every accepted job is accounted
		// for here, otherwise later jobs would be checked as if it didn't exist.
		List<HttpJobInternalDto> acceptedNarrowJobs = new ArrayList<>();

		while (!sortedToQueueJobsDeque.isEmpty()) {

			HttpJobInternalDto firstJob = sortedToQueueJobsDeque.pollFirst();

			ProgramSummaryRatesDto programRate = programSummaryRatesDtosMap.get(firstJob.programId());

			boolean abidesToProgramRateLimit = programRate.currentRate() + firstJob.rate() <= programRate.rateLimit();

			Boolean wideJobAbidesToEachHostRateLimit = false, narrowJobAbidesToTargetHostRateLimit = false;

			if (firstJob.scope() == HttpJobScope.WIDE) {

				if (abidesToProgramRateLimit)
					wideJobAbidesToEachHostRateLimit = checkAbidesToEachHostRateLimit(firstJob, programRate,
							acceptedNarrowJobs);
				else
					sortedToQueueJobsDeque.removeIf((job) -> job.scope().equals(HttpJobScope.WIDE)
							&& job.programId().equals(firstJob.programId()));

			} else {

				if (abidesToProgramRateLimit)
					narrowJobAbidesToTargetHostRateLimit = checkAbidesToHostRateLimit(firstJob, programRate,
							acceptedNarrowJobs);
				else
					sortedToQueueJobsDeque.removeIf((job) -> job.scope().equals(HttpJobScope.NARROW)
							&& job.hostId().equals(firstJob.hostId()) && job.duration().equals(firstJob.duration()));

			}

			if (abidesToProgramRateLimit
					&& (wideJobAbidesToEachHostRateLimit || narrowJobAbidesToTargetHostRateLimit)) {
				acceptedJobs.add(firstJob);

				boolean isWide = firstJob.scope() == HttpJobScope.WIDE;
				programSummaryRatesDtosMap.put(firstJob.programId(),
						programRate.withAcceptedJob(firstJob.rate(), isWide));
				if (!isWide)
					acceptedNarrowJobs.add(firstJob);
			}
		}

		return acceptedJobs;

	}

	// DONE:
	private boolean checkAbidesToEachHostRateLimit(HttpJobInternalDto jobDto, ProgramSummaryRatesDto programRate,
			List<HttpJobInternalDto> acceptedNarrowJobs) {

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

		// a wide job reaches every host of the program, so the wide jobs which are
		// already active count against each host, including those without narrow jobs
		if (hostRepo.anyInScopeProgramHostHasRateLimitLessThanGivenRate(jobDto.programId(),
				Math.toIntExact(jobDto.rate() + programRate.currentWideRate())))
			return false;

		List<NarrowHttpJobRateInternalDto> narrowJobDtos = new ArrayList<>(narrowJobRepo
				.findAllByProgramIdAndCurrentStateIn(jobDto.programId(),
						List.of(JobState.QUEUED, JobState.RUNNING, JobState.PAUSED)));

		acceptedNarrowJobs.stream().filter(accepted -> accepted.programId().equals(jobDto.programId()))
				.forEach(accepted -> narrowJobDtos
						.add(new NarrowHttpJobRateInternalDto(accepted.rate(), accepted.hostId())));

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
	private boolean checkAbidesToHostRateLimit(HttpJobInternalDto jobDto, ProgramSummaryRatesDto programRate,
			List<HttpJobInternalDto> acceptedNarrowJobs) {

		// check abides to host rate limit:
		// ((new job X rate + current Narrow rate of H + current Wide rate of Program of
		// H <= H rate limit))

		// if long check for long rate limit
		// if short check for short rate limit

		HostAllRateLimitsInternalDto hostDto = hostRepo.findRateLimitsAllDtoById(jobDto.hostId());

		List<NarrowHttpJobInternalDto> narrowJobsDtos = new ArrayList<>(narrowJobRepo.findAllByHostIdAndStateIn(
				jobDto.hostId(), List.of(JobState.QUEUED, JobState.RUNNING, JobState.PAUSED)));

		acceptedNarrowJobs.stream().filter(accepted -> accepted.hostId().equals(jobDto.hostId()))
				.forEach(accepted -> narrowJobsDtos
						.add(new NarrowHttpJobInternalDto(accepted.rate(), accepted.duration())));

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

}
