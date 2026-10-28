package com.automata.program;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.job.domain.model.enums.JobState;
import com.automata.job.repository.NarrowHttpJobRepository;
import com.automata.job.repository.WideHttpJobRepository;
import com.automata.program.common.dto.PatchProgramRequest;
import com.automata.program.common.exception.ProgramInUseException;
import com.automata.vulnerability.Vulnerability;
import com.automata.vulnerability.VulnerabilityRepository;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProgramService {

	private final ProgramRepository programRepo;

	private final VulnerabilityRepository vulnerabilityRepo;

	private final NarrowHttpJobRepository narrowJobRepo;

	private final WideHttpJobRepository wideJobRepo;

	public Program getProgramWithVulns(Long programId) {

		return programRepo.findByIdWithVulns(programId)
				.orElseThrow(() -> new EntityNotFoundException("Program not found"));

	}

	public Program createProgram(Program program) {

		return programRepo.save(program);

	}

	public Page<Program> getProgramsPagedAndFilteredOnQueryString(String query, Pageable pageable) {

		return programRepo.findByNameContainingIgnoreCase(query, pageable);

	}

	// The jobs that no longer (or not yet) use the rate of a program: drafts and every kind of finished job.
	// A state that is added later counts as in progress until it is listed here.
	private static final Set<JobState> STATES_NOT_USING_RATE = EnumSet.of(JobState.DRAFT, JobState.FINISHED,
			JobState.CANCELED, JobState.FAILED, JobState.SELFTERMINATED, JobState.LOSTCONTACT);

	// The jobs of a program are its narrow jobs (on its hosts) and its wide jobs
	private void requireNoJobInProgressOn(Program program) {

		Set<JobState> inProgress = EnumSet.complementOf(EnumSet.copyOf(STATES_NOT_USING_RATE));

		if (narrowJobRepo.existsByProgramIdAndCurrentStateIn(program.getId(), inProgress)
				|| wideJobRepo.existsByProgramIdAndCurrentStateIn(program.getId(), inProgress))
			throw new ProgramInUseException("The rate limit of program " + program.getId()
					+ " cannot be changed while jobs of the program are in progress (states " + inProgress + ")");

	}

	@Transactional
	public Program patchProgram(Long programId, PatchProgramRequest patchRequest) {

		Program program = programRepo.findById(programId)
				.orElseThrow(() -> new EntityNotFoundException("Program not found"));

		if (patchRequest.name() != null)
			program.setName(patchRequest.name());

		if (patchRequest.programRateLimit() != null) {

			if (patchRequest.programRateLimit() < 0)
				throw new IllegalArgumentException("programRateLimit cannot be negative");

			// a patch that leaves the limit as it is never looks at the jobs
			if (patchRequest.programRateLimit() != program.getProgramRateLimit()) {
				this.requireNoJobInProgressOn(program);
				program.setProgramRateLimit(patchRequest.programRateLimit());
			}

		}

		if (patchRequest.outOfScopeVulnIds() != null) {
			Set<Vulnerability> vulns = vulnerabilityRepo.findAllById(patchRequest.outOfScopeVulnIds()).stream()
					.collect(Collectors.toSet());

			program.setOutOfScopeVulns(vulns);
		}

		if (patchRequest.setLinkNull())
			program.setLink(null);
		else if (patchRequest.link() != null)
			program.setLink(patchRequest.link());

		if (patchRequest.setPlatformNull())
			program.setPlatform(null);
		else if (patchRequest.platform() != null)
			program.setPlatform(patchRequest.platform());

		programRepo.save(program);

		return program;

	}

	public void deleteProgram(Long programId) {

		throw new RuntimeException("Delete operation is not implemented yet");

	}

}
