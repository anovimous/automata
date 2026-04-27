package com.automata.job.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.NarrowJobTargetRequest;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.WideJobTargetHost;
import com.automata.job.domain.model.embedded.NarrowTargetConfig;
import com.automata.job.domain.model.embedded.WideTargetConfig;
import com.automata.job.repository.HttpJobRepository;
import com.automata.job.repository.NarrowJobTargetRequestRepository;
import com.automata.job.repository.WideJobTargetHostRepository;
import com.automata.request.Request;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobTargetConfigService {

	private final HttpJobRepository jobRepo;

	private final HostRepository hostRepo;

	private final NarrowJobTargetRequestRepository jobTargetRequestRepo;

	private final WideJobTargetHostRepository jobTargetHostRepo;

	@Transactional
	public NarrowHttpJob configureHostAsTarget(NarrowHttpJob newlyPersistedJob, Long hostId) {

		Host host = hostRepo.getReferenceById(hostId);

		NarrowTargetConfig targetconfig = NarrowTargetConfig.builder().targetHost(host).build();

		newlyPersistedJob.setTargetConfig(targetconfig);

		return jobRepo.save(newlyPersistedJob);

	}

	@Transactional
	public NarrowHttpJob configureRequestsAsTarget(NarrowHttpJob newlyPersistedJob, Set<Long> requestsIds) {

		List<NarrowJobTargetRequest> jobTargetRequestRelations = requestsIds.stream()
				.map((requestId) -> NarrowJobTargetRequest.of(newlyPersistedJob, Request.of(requestId))).toList();

		jobTargetRequestRepo.saveAll(jobTargetRequestRelations);

		NarrowTargetConfig targetconfig = NarrowTargetConfig.builder().targetRequests(jobTargetRequestRelations)
				.build();

		newlyPersistedJob.setTargetConfig(targetconfig);

		return jobRepo.save(newlyPersistedJob);

	}

	@Transactional
	public WideHttpJob configureHostsAsTarget(WideHttpJob newlyPersistedJob, Set<Long> hostsIds) {

		List<WideJobTargetHost> jobTargetHostRelations = hostsIds.stream()
				.map((hostId) -> WideJobTargetHost.of(newlyPersistedJob, Host.of(hostId))).toList();

		jobTargetHostRepo.saveAll(jobTargetHostRelations);

		WideTargetConfig targetConfig = WideTargetConfig.builder().targetHosts(jobTargetHostRelations).build();

		newlyPersistedJob.setTargetConfig(targetConfig);

		return newlyPersistedJob;
	}

}
