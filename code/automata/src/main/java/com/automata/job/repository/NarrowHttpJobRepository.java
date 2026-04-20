package com.automata.job.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.valueobject.HttpJobInternalDto;
import com.automata.job.domain.valueobject.NarrowHttpJobInternalDto;
import com.automata.job.domain.valueobject.NarrowHttpJobRateInternalDto;
import com.automata.tenant.authentication.Authentication;

@Repository
public interface NarrowHttpJobRepository extends JpaRepository<NarrowHttpJob, Long> {

	@Query("""
			SELECT new com.automata.job.domain.valueobject.HttpJobInternalDto(
				j.id,
				j.creationDate,
				j.scope,
				j.genericDetails.duration,
				j.genericDetails.priority,
				j.genericDetails.rate,
				j.program.id,
				j.host.id
			)
			FROM NarrowHttpJob j
			WHERE j.currentState = :currentState
			""")
	List<HttpJobInternalDto> getJobsDtosByCurrentState(@Param(value = "currentState") JobState currentState);

	@Query("SELECT new com.automata.job.domain.valueobject.NarrowHttpJobRateInternalDto(j.genericDetails.rate, j.host.id) FROM NarrowHttpJob j WHERE j.program.id = :programId AND j.currentState IN :states")
	List<NarrowHttpJobRateInternalDto> findAllByProgramIdAndCurrentStateIn(Long programId, List<JobState> states);

	@Query("SELECT new com.automata.job.domain.valueobject.NarrowHttpJobInternalDto(j.genericDetails.rate, j.duration) FROM NarrowHttpJob j WHERE j.host.id = :hostId AND j.currentState IN :states")
	List<NarrowHttpJobInternalDto> findAllByHostIdAndStateIn(Long hostId, List<JobState> states);

	@Query("SELECT j.tenant.authentication FROM NarrowHttpJob j WHERE j.id = :jobId")
	Authentication findAuthenticationOfJob(@Param("jobId") Long jobId);

}