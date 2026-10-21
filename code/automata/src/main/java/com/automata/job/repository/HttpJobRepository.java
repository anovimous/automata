package com.automata.job.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.enums.ConfigFileStatus;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
import com.automata.job.domain.valueobject.HttpJobRoutineInternalDto;
import com.automata.job.domain.valueobject.ProgramRateDto;

@Repository
public interface HttpJobRepository extends JpaRepository<HttpJob, Long>, JpaSpecificationExecutor<HttpJob> {

	@Query("""
			    SELECT new com.automata.job.domain.valueobject.ProgramRateDto(
			        p.id,
			        p.programRateLimit,
			        COALESCE(SUM(j.genericDetails.rate), 0)
			    )
			    FROM Program p
			    LEFT JOIN HttpJob j ON j.program.id = p.id
			        AND j.genericDetails.currentState IN :states
			    WHERE p.id IN :programsIds
			    GROUP BY p.id, p.programRateLimit
			""")
	List<ProgramRateDto> getProgramsRateDtos(@Param(value = "programsIds") Set<Long> programsIds,
			@Param(value = "states") Set<JobState> states);

	@Query("""
			SELECT new com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto(
			    j.id,
			    j.genericDetails.httpJobScope,
			    j.genericDetails.currentState,
			    j.genericDetails.requestedState,
			    j.genericDetails.verbosity,
			    j.genericDetails.priority,
			    j.genericDetails.rate,
			    j.genericDetails.targetSelector,
			    j.genericDetails.genericConfig,
			    j.genericDetails.customConfig,
			    j.program.id,
			    j.program.name,
			    j.routine.id,
			    j.routine.key,
			    j.result.failureMsg,
			    j.genericDetails.configFileStatus,
			    j.genericDetails.configFileFailure,
			    j.aiDescription,
			    j.aiResult,
			    j.createdBy,
			    j.approved
			)
			FROM HttpJob j
			LEFT JOIN j.program
			JOIN j.routine
			WHERE j.id = :jobId
			""")
	Optional<HttpJobFullDetailsInternalDto> findFullDetailsByJobId(@Param("jobId") Long jobId);

	// VERSIONED bumps the version, so that who loaded a job before this update fails
	// when saving it. Only the jobs which are still in the expected state are
	// updated, since the user may have changed them since they were read.
	@Query("""
			UPDATE VERSIONED HttpJob j
			SET j.genericDetails.currentState = :state
			WHERE j.id IN :finalQueuedJobsIds AND j.genericDetails.currentState = :expectedState
			""")
	@Modifying
	void updateJobsState(@Param("finalQueuedJobsIds") Set<Long> finalQueuedJobsIds, @Param("state") JobState state,
			@Param("expectedState") JobState expectedState);

	// Not VERSIONED on purpose: a build of the files running in the background must
	// not make the user's concurrent pause or cancel fail. The expected status keeps
	// a late build from overwriting a newer one. Returns the number of updated rows.
	@Query("""
			UPDATE HttpJob j
			SET j.genericDetails.configFileStatus = :status,
			    j.genericDetails.configFileFailure = :failure,
			    j.genericDetails.configFileAttempts = :attempts
			WHERE j.id = :jobId AND j.genericDetails.configFileStatus = :expectedStatus
			""")
	@Modifying
	@Transactional
	int updateConfigFileStatus(@Param("jobId") Long jobId, @Param("expectedStatus") ConfigFileStatus expectedStatus,
			@Param("status") ConfigFileStatus status, @Param("failure") String failure,
			@Param("attempts") Integer attempts);

	// Not VERSIONED on purpose: a worker asking about its job every few seconds
	// must not make the user's concurrent pause or cancel fail. Returns the number
	// of updated rows, which is 0 for an unknown job.
	@Query("""
			UPDATE HttpJob j
			SET j.genericDetails.lastWorkerContactAt = :now
			WHERE j.id = :jobId
			""")
	@Modifying
	@Transactional
	int updateLastWorkerContact(@Param("jobId") Long jobId, @Param("now") Instant now);

	// The jobs without contact are the ones with a contact older than the
	// threshold. A job a worker never got in touch about has none, so it is not found.
	@Query("""
			SELECT j FROM HttpJob j
			WHERE j.genericDetails.currentState IN :states
			    AND j.genericDetails.lastWorkerContactAt < :threshold
			""")
	List<HttpJob> findAllByCurrentStateInAndLastWorkerContactBefore(@Param("states") Collection<JobState> states,
			@Param("threshold") Instant threshold);

	@Query("SELECT new com.automata.job.domain.valueobject.HttpJobRoutineInternalDto(j.id, j.routine.key) FROM HttpJob j WHERE j.id IN :jobsIds")
	List<HttpJobRoutineInternalDto> findJobsRoutineKeys(@Param("jobsIds") List<Long> jobsIds);

	@Query("SELECT w.path FROM HttpJob j JOIN j.wordlists w WHERE j.id = :jobId")
	Set<String> findWordlistPathsByJobId(@Param("jobId") Long jobId);

}
