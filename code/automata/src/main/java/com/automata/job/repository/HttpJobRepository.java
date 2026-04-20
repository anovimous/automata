package com.automata.job.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.ProgramRateDto;

@Repository
public interface HttpJobRepository extends JpaRepository<HttpJob, Long> {

	@Query("""
			    SELECT new com.automata.job.domain.valueobject.ProgramRateDto(
			        j.programId,
			        j.program.programRateLimit,
			        SUM(j.genericDetails.rate)
			    )
			    FROM HttpJob j
			    WHERE j.program.id IN :programsIds
			    AND j.genericDetails.currentState IN :states
			    GROUP BY j.programId, j.program.programRateLimit
			""")
	List<ProgramRateDto> getProgramsRateDtos(@Param(value = "programsIds") Set<Long> programsIds,
			@Param(value = "states") Set<JobState> states);

}
