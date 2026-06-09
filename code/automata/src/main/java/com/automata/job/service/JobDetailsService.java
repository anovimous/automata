package com.automata.job.service;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.domain.model.enums.TargetType;
import com.automata.job.domain.valueobject.JobDetailsFileContainer;
import com.automata.job.repository.HttpJobRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.tenant.authentication.Authentication;
import com.automata.tenant.authentication.AuthenticationRepository;
import com.automata.tenant.authentication.StaticAuthData;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobDetailsService {

	private final HttpJobRepository httpJobRepo;

	private final AuthenticationRepository authRepo;

	private final RoutineRepository routineRepo;

	@Transactional(readOnly = true)
	public JobDetailsFileContainer createJobDetailsContainer(HttpJob job) {

		TargetType targetType = switch (job.getGenericDetails().getTargetSelector().getSelectorType()) {
		case SelectorType.SINGLE_HOST, SelectorType.MULTIPLE_HOSTS -> TargetType.HOST;
		default -> TargetType.REQUEST;

		};
		Routine routine = routineRepo.findById(job.getRoutine().getId())
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

		StaticAuthData authData = null;

		if (job.getGenericDetails().getHttpJobScope() == HttpJobScope.NARROW) {
			NarrowHttpJob castedJob = (NarrowHttpJob) job;
			if (castedJob.getTenant() != null) {
				Optional<Authentication> auth = authRepo.findByTenant(castedJob.getTenant());
				if (auth.isPresent())
					authData = auth.get().getAuthData();

			}
		}

		Set<String> wordlistsPaths = httpJobRepo.findWordlistPathsByJobId(job.getId());
		log.info("Wordlist paths  size in jobDetailsService:" + wordlistsPaths.size());
		return JobDetailsFileContainer.builder().jobId(job.getId()).rate(job.getGenericDetails().getRate())
				.verbosity(job.getGenericDetails().getVerbosity()).targetType(targetType).routineKey(routine.getKey())
				.auth(authData).wordlistsPaths(wordlistsPaths).customConfig(job.getGenericDetails().getCustomConfig())
				.genericConfig(job.getGenericDetails().getGenericConfig()).build();

	}

}
