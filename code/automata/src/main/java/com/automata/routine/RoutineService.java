package com.automata.routine;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.automata.job.JobRepository;
import com.automata.routine.common.dto.RoutinePatchRequest;
import com.automata.vulnerability.Vulnerability;
import com.automata.vulnerability.VulnerabilityRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoutineService {

	private final RoutineRepository routineRepo;

	private final VulnerabilityRepository vulnRepo;

	private final JobRepository jobRepo;

	public Routine getRoutineById(Long routineId) {

		return routineRepo.findById(routineId).orElseThrow(() -> new EntityNotFoundException("Routine not found"));

	}

	public Page<Routine> getRoutinesPagedAndFilteredOnQueryString(String name, Pageable pageable) {
		return routineRepo.findByNameContainingIgnoreCase(name, pageable);
	}

	public Page<Routine> getVulnerabilityRoutinesPagedAndFilteredOnQueryString(Long vulnId, String name,
			Pageable pageable) {

		Vulnerability vulnerability = vulnRepo.findById(vulnId)
				.orElseThrow(() -> new EntityNotFoundException("Vulnerability not found"));

		return routineRepo.findByNameContainingIgnoreCaseAndVulnerability(name, vulnerability, pageable);
	}

	public Routine createRoutine(Routine toBeCreatedRoutine, Long vulnId) {

		Vulnerability vulnerability = vulnRepo.findById(vulnId).orElse(null);

		toBeCreatedRoutine.setVulnerability(vulnerability);

		return routineRepo.save(toBeCreatedRoutine);

	}

	public Routine patchRoutine(Long routineId, RoutinePatchRequest patchRequest) {

		Routine routine = routineRepo.findById(routineId)
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

		patchRequest.name().ifPresent(routine::setName);

		if (patchRequest.setDescriptionNull())
			routine.setDescription(null);
		else
			patchRequest.description().ifPresent(routine::setName);

		patchRequest.isAvailableAtConsumer().ifPresent(routine::setAvailableAtConsumer);

		patchRequest.overhead().ifPresent(routine::setOverhead);

		patchRequest.permittedScope().ifPresent(routine::setScope);

		patchRequest.protocol().ifPresent(routine::setProtocol);

		if (patchRequest.setVulnerabilityNull())
			routine.setVulnerability(null);
		else
			patchRequest.vulnerabilityId().ifPresent((vulnId) -> {

				Vulnerability vulnerability = vulnRepo.findById(vulnId)
						.orElseThrow(() -> new EntityNotFoundException("Vulnerability not found"));

				routine.setVulnerability(vulnerability);

			});

		return routineRepo.save(routine);

	}

	public void deleteRoutine(Long routineId) {

		Routine routine = routineRepo.findById(routineId)
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

		if (jobRepo.existsByRoutine(routine))
			throw new RuntimeException("Routine can't be deleted if any job is tied to it");

		routineRepo.delete(routine);

	}

}
