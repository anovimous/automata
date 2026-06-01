package com.automata.host;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.host.common.dto.HostAllRateLimitsInternalDto;
import com.automata.host.common.dto.HostRateLimitInternalDto;
import com.automata.program.Program;

@Repository
public interface HostRepository extends JpaRepository<Host, Long> {

	Page<Host> findByHostContainingIgnoreCase(String query, Pageable pageable);

	Page<Host> findByHostContainingIgnoreCaseAndProgram(String name, Program program, Pageable pageable);

	@Query("""
			    SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END
			    FROM Host h
			    WHERE h.program.id = :programId
			    AND h.outOfScope = false
			    AND h.hostRateLimit < :rate
			""")
	boolean anyInScopeProgramHostHasRateLimitLessThanGivenRate(Long programId, Integer rate);

	@Query("SELECT new com.automata.host.common.dto.HostRateLimitInternalDto(h.id, h.hostRateLimit) FROM Host h WHERE h.id IN :hostsIds")
	List<HostRateLimitInternalDto> findDtosByIds(@Param(value = "hostsIds") Set<Long> hostsIds);

	@Query("SELECT new com.automata.host.common.dto.HostAllRateLimitsInternalDto(h.id, h.hostRateLimit, h.shortRateLimit, h.longRateLimit) FROM Host h WHERE h.id = :hostId")
	HostAllRateLimitsInternalDto findRateLimitsAllDtoById(@Param(value = "hostId") Long hostId);

	Optional<Host> findByHost(String host);

}
