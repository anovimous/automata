package com.automata.host;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.program.Program;
import com.automata.program.ProgramRepository;
import com.automata.host.common.dto.HostCreationRequest;
import com.automata.host.common.dto.PatchHostRequest;
import com.automata.host.common.exception.HostInUseException;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.repository.NarrowHttpJobRepository;
import com.automata.job.repository.WideHttpJobRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HostService {

	private final HostRepository hostRepo;

	private final ProgramRepository programRepo;

	private final NarrowHttpJobRepository narrowJobRepo;

	private final WideHttpJobRepository wideJobRepo;

	// The jobs that no longer (or not yet) use the rate of a host: drafts and every kind of finished job.
	// A state that is added later counts as in progress until it is listed here.
	private static final Set<JobState> STATES_NOT_USING_RATE = EnumSet.of(JobState.DRAFT, JobState.FINISHED,
			JobState.CANCELED, JobState.FAILED, JobState.SELFTERMINATED, JobState.LOSTCONTACT);

	public Host getHostById(Long hostId) {

		return hostRepo.findById(hostId).orElseThrow(() -> new EntityNotFoundException("Host not found"));

	}

	@Transactional
	public Host createHost(HostCreationRequest request, Host host) {

		this.applyRateLimitsOfNewHost(request, host);

		Long programId = request.programId();

		Program program = programRepo.findById(programId)
				.orElseThrow(() -> new EntityNotFoundException("Program not found"));

		host.setHost(host.getHost().toLowerCase());

		HostUtils.validateHostFormat(host.getHost())
				.ifNotValidThrow(() -> new IllegalArgumentException("Host is of invalid format"));

		host.setLevel(HostUtils.identifyLevel(host.getHost()));

		host.setProgram(program);

		return hostRepo.save(host);

	}

	// The jobs that act on a host are its narrow jobs and the wide jobs of its program
	private void requireNoJobInProgressOn(Host host) {

		Set<JobState> inProgress = EnumSet.complementOf(EnumSet.copyOf(STATES_NOT_USING_RATE));

		if (narrowJobRepo.existsByHostIdAndCurrentStateIn(host.getId(), inProgress)
				|| wideJobRepo.existsByProgramIdAndCurrentStateIn(host.getProgram().getId(), inProgress))
			throw new HostInUseException("The rate limits of host " + host.getId()
					+ " cannot be changed while jobs that act on it are in progress (states " + inProgress + ")");

	}

	// A host always gets its rate limits when it is created (0 is allowed: no job can run on such a host)
	private void applyRateLimitsOfNewHost(HostCreationRequest request, Host host) {

		if (request.hostRateLimit() == null || request.shortRateLimit() == null || request.longRateLimit() == null)
			throw new IllegalArgumentException("hostRateLimit, shortRateLimit and longRateLimit are required");

		if (request.hostRateLimit() < 0 || request.shortRateLimit() < 0 || request.longRateLimit() < 0)
			throw new IllegalArgumentException("Rate limits cannot be negative");

		HostUtils.validateRateLimits(request.hostRateLimit(), request.longRateLimit(), request.shortRateLimit())
				.ifNotValidThrow(() -> new IllegalArgumentException("Rate limit values are not compatible"));

		host.setHostRateLimit(request.hostRateLimit());
		host.setShortRateLimit(request.shortRateLimit());
		host.setLongRateLimit(request.longRateLimit());

	}

	public Page<Host> getHostsPagedAndFilteredOnQueryString(String name, Pageable pageable) {

		return hostRepo.findByHostContainingIgnoreCase(name, pageable);

	}

	@Transactional(readOnly = true)
	public Page<Host> getProgramHostsPagedAndFilteredOnQueryString(Long programId, String name, Pageable pageable) {

		Program program = programRepo.findById(programId)
				.orElseThrow(() -> new EntityNotFoundException("Program not found"));

		return hostRepo.findByHostContainingIgnoreCaseAndProgram(name, program, pageable);
	}

	@Transactional
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

		if (hostRateLimit < 0 || longRateLimit < 0 || shortRateLimit < 0)
			throw new IllegalArgumentException("Rate limits cannot be negative");

		HostUtils.validateRateLimits(hostRateLimit, longRateLimit, shortRateLimit)
				.ifNotValidThrow(() -> new IllegalArgumentException("Rate limit values are not compatible"));

		boolean limitsChange = hostRateLimit != host.getHostRateLimit() || longRateLimit != host.getLongRateLimit()
				|| shortRateLimit != host.getShortRateLimit();

		if (limitsChange) {

			this.requireNoJobInProgressOn(host);

			host.setHostRateLimit(hostRateLimit);

			host.setLongRateLimit(longRateLimit);

			host.setShortRateLimit(shortRateLimit);

		}

		if (patchRequest.scope() != null)
			host.setScope(patchRequest.scope());

		if (patchRequest.description() != null)
			host.setDescription(patchRequest.description());

		if (patchRequest.technologies() != null)
			host.setTechnologies(patchRequest.technologies());

		if (patchRequest.notes() != null)
			host.setNotes(patchRequest.notes());

		hostRepo.save(host);

		return host;

	}

	public void deleteHost(Long hostId) {
		throw new RuntimeException("Delete operation is not implemented yet");
	}

}
