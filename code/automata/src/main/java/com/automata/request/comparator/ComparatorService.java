package com.automata.request.comparator;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.program.Program;
import com.automata.program.ProgramRepository;
import com.automata.request.comparator.common.dto.ComparatorPatchRequest;
import com.automata.request.comparator.common.enums.Schema;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComparatorService {

	private final ComparatorRepository comparatorRepo;

	private final ProgramRepository programRepo;

	private final HostRepository hostRepo;

	public Comparator getComparatorById(Long comparatorId) {

		return comparatorRepo.findById(comparatorId)
				.orElseThrow(() -> new EntityNotFoundException("Comparator not found"));

	}

	public Page<Comparator> getComparatorsPagedAndFiltered(String name, Long programId, Long hostId, Schema schema,
			Pageable pageable) {

		Specification<Comparator> spec = ComparatorUtils.buildSpecification(name, programId, hostId, schema);

		return comparatorRepo.findAll(spec, pageable);

	}

	public Comparator createComparator(Comparator comparator) {

		return comparatorRepo.save(comparator);

	}

	@Transactional
	public Comparator patchComparator(Long comparatorId, ComparatorPatchRequest patchRequest) {

		Comparator comparator = comparatorRepo.findById(comparatorId)
				.orElseThrow(() -> new EntityNotFoundException("Comparator not found"));

		patchRequest.name().ifPresent(comparator::setName);

		if (patchRequest.setSchemaNull())
			comparator.setSchema(null);
		else
			patchRequest.schema().ifPresent(comparator::setSchema);

		patchRequest.programId().ifPresent((programId) -> {
			Program program = programRepo.findById(programId)
					.orElseThrow(() -> new EntityNotFoundException("Program not found"));
			comparator.setProgram(program);
		});

		patchRequest.hostId().ifPresent((hostId) -> {
			if (comparator.getProgram() == null)
				throw new IllegalArgumentException("Host can't be set without the program");
			Host host = hostRepo.findById(hostId).orElseThrow(() -> new EntityNotFoundException("Host not found"));
			if (host.getProgram().getId() != comparator.getProgram().getId())
				throw new IllegalArgumentException(
						"Comparator can't have a host belonging to other than the comparator program");
			comparator.setHost(host);
		});

		comparatorRepo.save(comparator);

		return comparator;

	}

	@Transactional
	public void deleteComparator(Long comparatorId) {

		Comparator comparator = comparatorRepo.findById(comparatorId)
				.orElseThrow(() -> new EntityNotFoundException("Comparator not found"));

		comparatorRepo.delete(comparator);

	}

}
