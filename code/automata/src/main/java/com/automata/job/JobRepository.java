package com.automata.job;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.automata.job.enums.JobState;
import com.automata.program.Program;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

	boolean ExistsByProgramAndStateNotIn(Program program, List<JobState> of);
	
}
