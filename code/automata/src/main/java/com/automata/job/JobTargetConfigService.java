package com.automata.job;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.job.targetconfig.NarrowTargetConfig;
import com.automata.job.targetconfig.WideTargetConfig;
import com.automata.request.Request;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobTargetConfigService {

	private final HttpJobRepository jobRepo;

	private final HostRepository hostRepo;

	private final NarrowJobTargetRequestRepository jobTargetRequestRepo;

	private final WideJobTargetHostRepository jobTargetHostRepo;

	public NarrowHttpJob configureHostAsTarget(NarrowHttpJob newlyPersistedJob, Long hostId) {

		Host host = hostRepo.getReferenceById(hostId);

		NarrowTargetConfig targetconfig = NarrowTargetConfig.builder().targetHost(host).build();

		newlyPersistedJob.setTargetConfig(targetconfig);

		return jobRepo.save(newlyPersistedJob);

	}

	public NarrowHttpJob configureRequestsAsTarget(NarrowHttpJob newlyPersistedJob, Set<Long> requestsIds) {

		List<NarrowJobTargetRequest> jobTargetRequestRelations = requestsIds.stream()
				.map((requestId) -> NarrowJobTargetRequest.of(newlyPersistedJob, Request.of(requestId))).toList();

		jobTargetRequestRepo.saveAll(jobTargetRequestRelations);

		NarrowTargetConfig targetconfig = NarrowTargetConfig.builder().targetRequests(jobTargetRequestRelations)
				.build();

		newlyPersistedJob.setTargetConfig(targetconfig);

		return newlyPersistedJob;

	}

	public WideHttpJob configureHostsAsTarget(WideHttpJob newlyPersistedJob, Set<Long> hostsIds) {

		List<WideJobTargetHost> jobTargetHostRelations = hostsIds.stream()
				.map((hostId) -> WideJobTargetHost.of(newlyPersistedJob, Host.of(hostId))).toList();

		jobTargetHostRepo.saveAll(jobTargetHostRelations);

		WideTargetConfig targetConfig = WideTargetConfig.builder().targetHosts(jobTargetHostRelations).build();

		newlyPersistedJob.setTargetConfig(targetConfig);

		return newlyPersistedJob;
	}

}
