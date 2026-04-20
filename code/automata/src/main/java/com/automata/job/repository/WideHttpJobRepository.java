package com.automata.job.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobInternalDto;
import com.automata.job.domain.valueobject.ProgramCurrentWideRateDto;

@Repository
public interface WideHttpJobRepository extends JpaRepository<WideHttpJob, Long> {

	@Query("""
			SELECT new com.automata.job.domain.valueobject.HttpJobInternalDto(
				j.id,
				j.creationDate,
				j.scope,
				j.genericDetails.duration,
				j.genericDetails.priority,
				j.genericDetails.rate,
				j.program.id,
				CAST(null AS Long)
			)
			FROM WideHttpJob j
			WHERE j.currentState = :currentState
			""")
	List<HttpJobInternalDto> getJobsDtosByCurrentState(@Param(value = "currentState") JobState currentState);

	@Query("""
			    SELECT new com.automata.job.domain.valueobject.ProgramCurrentWideRateDto(
			        j.programId,
			        SUM(j.genericDetails.rate)
			    )
			    FROM HttpJob j
			    WHERE j.program.id IN :programsIds
			    AND j.genericDetails.currentState IN :states
			    GROUP BY j.programId
			""")
	List<ProgramCurrentWideRateDto> getProgramsWideRateDtos(@Param(value = "programsIds") Set<Long> programsIds,
			@Param(value = "states") Set<JobState> states);

}