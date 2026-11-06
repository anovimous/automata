package com.automata.agentcontext;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentContextRepository extends JpaRepository<AgentContext, Long> {

	Optional<AgentContext> findByUuid(UUID uuid);

	Page<AgentContext> findByHostId(Long hostId, Pageable pageable);

	boolean existsByTenantsId(Long tenantId);

}
