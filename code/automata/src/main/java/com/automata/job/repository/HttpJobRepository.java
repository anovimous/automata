package com.automata.job.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
import com.automata.job.domain.valueobject.ProgramRateDto;
import com.automata.routine.Routine;

@Repository
public interface HttpJobRepository extends JpaRepository<HttpJob, Long> {

	@Query("""
			    SELECT new com.automata.job.domain.valueobject.ProgramRateDto(
			        j.program.id,
			        j.program.programRateLimit,
			        SUM(j.genericDetails.rate)
			    )
			    FROM HttpJob j
			    WHERE j.program.id IN :programsIds
			    AND j.genericDetails.currentState IN :states
			    GROUP BY j.program.id, j.program.programRateLimit
			""")
	List<ProgramRateDto> getProgramsRateDtos(@Param(value = "programsIds") Set<Long> programsIds,
			@Param(value = "states") Set<JobState> states);

	@Query("SELECT j.routine FROM HttpJob j WHERE j.id = :jobId")
	Routine getRoutineOfJob(@Param(value = "jobId") Long jobId);

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

}
