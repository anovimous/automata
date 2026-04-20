package com.automata.job.repository;

import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.automata.host.common.dto.HostInternalDto;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.WideJobTargetHost;

@Repository
public interface WideJobTargetHostRepository extends JpaRepository<WideJobTargetHost, Long> {

	@Query(value = """
			SELECT new com.automata.host.common.dto.HostInternalDto(
			    h.id,
			    h.host,
			    h.scope
			)
			FROM WideJobTargetHost h
			WHERE h.job = :job
			""", countQuery = "SELECT COUNT(h) FROM WideJobTargetHost h WHERE h.job = :job")
	Stream<HostInternalDto> getHostDtosByJob(WideHttpJob fullyConfiguredJob);

}
