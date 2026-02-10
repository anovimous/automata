package com.automata.job;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.host.Host;
import com.automata.job.enums.JobState;
import com.automata.program.Program;
import com.automata.request.Request;
import com.automata.routine.Routine;

@Repository
public interface RunJobRepository extends JpaRepository<RunJob, Long> {

	boolean ExistsByProgramAndStateNotIn(Program program, List<JobState> states);

	@Query("""
		    select case
		        when count(j) > 0 then true else false
		    end
		    from Job j
		    where j.program.id = :programId
		      and (j.host is null or j.host = :host)
		      and j.state not in :states
		""")
		boolean checkExistenceOfJobsThatAffectHostCurrentRate(
		        @Param("programId") Long programId,
		        @Param("host") Host host,
		        @Param("states") List<JobState> states
		);

	boolean existsByRoutine(Routine routine);

	boolean existsByRequest(Request request);


}
