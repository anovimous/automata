package com.automata.job.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
import com.automata.job.domain.valueobject.HttpJobRoutineInternalDto;
import com.automata.job.domain.valueobject.ProgramRateDto;

@Repository
public interface HttpJobRepository extends JpaRepository<HttpJob, Long> {

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
			    j.routine.key
			)
			FROM HttpJob j
			LEFT JOIN j.program
			JOIN j.routine
			WHERE j.id = :jobId
			""")
	Optional<HttpJobFullDetailsInternalDto> findFullDetailsByJobId(@Param("jobId") Long jobId);

	@Query("UPDATE HttpJob j SET j.genericDetails.currentState = :state WHERE j.id IN :finalQueuedJobsIds")
	@Modifying
	void updateJobsState(@Param("finalQueuedJobsIds") Set<Long> finalQueuedJobsIds, @Param("state") JobState state);

	@Query("SELECT new com.automata.job.domain.valueobject.HttpJobRoutineInternalDto(j.id, j.routine.key) FROM HttpJob j WHERE j.id IN :jobsIds")
	List<HttpJobRoutineInternalDto> findJobsRoutineKeys(@Param("jobsIds") List<Long> jobsIds);

}
