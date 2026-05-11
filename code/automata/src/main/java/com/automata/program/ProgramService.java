package com.automata.program;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.program.common.dto.PatchProgramRequest;
import com.automata.vulnerability.Vulnerability;
import com.automata.vulnerability.VulnerabilityRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProgramService {

	private final ProgramRepository programRepo;

	private final VulnerabilityRepository vulnerabilityRepo;

	public Program getProgramById(Long programId) {

		return programRepo.findById(programId).orElseThrow(() -> new EntityNotFoundException("Program not found"));

	}

	public Program createProgram(Program program) {

		return programRepo.save(program);

	}

	public Page<Program> getProgramsPagedAndFilteredOnQueryString(String query, Pageable pageable) {

		return programRepo.findByNameContainingIgnoreCase(query, pageable);

	}

	@Transactional
	public Program patchProgram(Long programId, PatchProgramRequest patchRequest) {

		Program program = programRepo.findById(programId)
				.orElseThrow(() -> new EntityNotFoundException("Program not found"));

		if (patchRequest.name() != null)
			program.setName(patchRequest.name());

//		if (patchRequest.programRateLimit() != null && !jobRepo.ExistsByProgramAndStateNotIn(program,
//				List.of(JobState.FINISHED, JobState.CANCELED, JobState.FAILED)))
//			program.setProgramRateLimit(patchRequest.programRateLimit());

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
