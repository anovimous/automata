package com.automata.host;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.automata.program.Program;
import com.automata.program.ProgramRepository;
import com.automata.host.common.dto.PatchHostRequest;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HostService {

	private final HostRepository hostRepo;

	private final ProgramRepository programRepo;

	public Host getHostById(Long hostId) {

		return hostRepo.findById(hostId).orElseThrow(() -> new EntityNotFoundException("Host not found"));

	}

	public Host createHost(Host host, Long programId) {

		Program program = programRepo.findById(programId)
				.orElseThrow(() -> new EntityNotFoundException("Program not found"));

		HostUtils.validateHostFormat(host.getHost())
				.ifNotValidThrow(() -> new IllegalArgumentException("Host is of invalid format"));

		host.setProgram(program);

		return hostRepo.save(host);

	}

	public Page<Host> getHostsPagedAndFilteredOnQueryString(String name, Pageable pageable) {

		return hostRepo.findByHostContainingIgnoreCase(name, pageable);

	}

	public Page<Host> getProgramHostsPagedAndFilteredOnQueryString(Long programId, String name, Pageable pageable) {

		Program program = programRepo.findById(programId)
				.orElseThrow(() -> new EntityNotFoundException("Program not found"));

		return hostRepo.findByHostContainingIgnoreCaseAndProgram(name, program, pageable);
	}

	public Host patchHost(Long hostId, PatchHostRequest patchRequest) {

		Host host = hostRepo.findById(hostId).orElseThrow(() -> new EntityNotFoundException("Host not found"));

		if (patchRequest.host() != null) {
			HostUtils.validateHostFormat(host.getHost())
					.ifNotValidThrow(() -> new IllegalArgumentException("Host is of invalid format"));
			host.setHost(patchRequest.host());
			host.setLevel(HostUtils.identifyLevel(host.getHost()));
		}

		int hostRateLimit = patchRequest.hostRateLimit() == null ? host.getHostRateLimit()
				: patchRequest.hostRateLimit();

		int longRateLimit = patchRequest.longRateLimit() == null ? host.getLongRateLimit()
				: patchRequest.longRateLimit();

		int shortRateLimit = patchRequest.shortRateLimit() == null ? host.getShortRateLimit()
				: patchRequest.shortRateLimit();

		HostUtils.validateRateLimits(hostRateLimit, longRateLimit, shortRateLimit)
				.ifNotValidThrow(() -> new IllegalArgumentException("Rate limit values are not compatible"));

//		if (jobRepo.checkExistenceOfJobsThatAffectHostCurrentRate(host.getProgram().getId(), host,
//				List.of(JobState.FINISHED, JobState.CANCELED, JobState.FAILED))) {
//
//			host.setHostRateLimit(hostRateLimit);
//
//			host.setLongRateLimit(longRateLimit);
//
//			host.setShortRateLimit(shortRateLimit);
//
//		}

		if (patchRequest.scope() != null)
			host.setScope(patchRequest.scope());

		hostRepo.save(host);

		return host;

	}

	public void deleteHost(Long hostId) {
		// TODO Later
		// lower priority
	}

}
