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
				j.genericDetails.httpJobScope,
				CAST(null AS com.automata.routine.common.enums.Duration),
				j.genericDetails.priority,
				j.genericDetails.rate,
				j.program.id,
				CAST(null AS Long)
			)
			FROM WideHttpJob j
			WHERE j.genericDetails.currentState = :currentState
			""")
	List<HttpJobInternalDto> getJobsDtosByCurrentState(@Param(value = "currentState") JobState currentState);

	@Query("""
		    SELECT new com.automata.job.domain.valueobject.ProgramCurrentWideRateDto(
		        p.id,
		        COALESCE(SUM(j.genericDetails.rate), 0)
		    )
		    FROM Program p
		    LEFT JOIN HttpJob j ON j.program.id = p.id
		        AND j.genericDetails.currentState IN :states
		    WHERE p.id IN :programsIds
		    GROUP BY p.id
		""")
	List<ProgramCurrentWideRateDto> getProgramsWideRateDtos(@Param(value = "programsIds") Set<Long> programsIds,
			@Param(value = "states") Set<JobState> states);

}