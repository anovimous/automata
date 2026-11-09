package com.automata.routine;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.common.exception.AlreadyExistsException;
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

	public Routine getRoutineById(Long routineId) {

		return routineRepo.findById(routineId).orElseThrow(() -> new EntityNotFoundException("Routine not found"));

	}

	public Page<Routine> getRoutinesPagedAndFilteredOnQueryString(String name, Pageable pageable) {
		return routineRepo.findByKeyContainingIgnoreCase(name, pageable);
	}

	@Transactional(readOnly = true)
	public Page<Routine> getVulnerabilityRoutinesPagedAndFilteredOnQueryString(Long vulnId, String name,
			Pageable pageable) {

		Vulnerability vulnerability = vulnRepo.findById(vulnId)
				.orElseThrow(() -> new EntityNotFoundException("Vulnerability not found"));

		return routineRepo.findByKeyContainingIgnoreCaseAndVulnerability(name, vulnerability, pageable);
	}

	@Transactional
	public Routine createRoutine(Routine toBeCreatedRoutine, Long vulnId) {

		if (toBeCreatedRoutine.getResultContentType() == null)
			throw new IllegalArgumentException("resultContentType is required");

		if (toBeCreatedRoutine.getKey() != null && routineRepo.existsByKey(toBeCreatedRoutine.getKey()))
			throw new AlreadyExistsException("Routine '" + toBeCreatedRoutine.getKey() + "' already exists");

		Vulnerability vulnerability = null;

		if (vulnId != null)
			vulnerability = vulnRepo.findById(vulnId)
					.orElseThrow(() -> new EntityNotFoundException("Vulnerability not found"));

		toBeCreatedRoutine.setVulnerability(vulnerability);

		return routineRepo.save(toBeCreatedRoutine);

	}

	@Transactional
	public Routine patchRoutine(Long routineId, RoutinePatchRequest patchRequest) {

		Routine routine = routineRepo.findById(routineId)
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

		patchRequest.description().ifPresent(description -> {
			requireNotBlank(description, "description");
			routine.setDescription(description);
		});

		patchRequest.usageDescriptionForAgent().ifPresent(usageDescription -> {
			requireValidUsageDescriptionForAgent(usageDescription);
			routine.setUsageDescriptionForAgent(usageDescription);
		});

		patchRequest.isAvailableAtConsumer().ifPresent(routine::setAvailableAtConsumer);

		patchRequest.wordlistRequired().ifPresent(routine::setWordlistRequired);

		if (patchRequest.allowedTargets() != null && !patchRequest.allowedTargets().isEmpty())
			routine.setAllowedTargets(patchRequest.allowedTargets());

		if (patchRequest.setConfigSchemaNull())
			routine.setConfigSchema(null);
		else
			patchRequest.configSchema().ifPresent(routine::setConfigSchema);

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

	static void requireValidUsageDescriptionForAgent(String usageDescription) {

		requireNotBlank(usageDescription, "usageDescriptionForAgent");

		if (usageDescription.length() > Routine.MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH)
			throw new IllegalArgumentException("usageDescriptionForAgent is too long, the limit is "
					+ Routine.MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH + " characters");
	}

	private static void requireNotBlank(String value, String field) {

		if (value.isBlank())
			throw new IllegalArgumentException(field + " must not be blank");
	}

	@Transactional
	public void deleteRoutine(Long routineId) {

		throw new UnsupportedOperationException("Deleting a routine is not implemented yet");

//		Routine routine = routineRepo.findById(routineId)
//				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

//		IF (JOBREPO.EXISTSBYROUTINE(ROUTINE))
//			THROW NEW RUNTIMEEXCEPTION("ROUTINE CAN'T BE DELETED IF ANY JOB IS TIED TO IT");

//		routineRepo.delete(routine);

	}

}
