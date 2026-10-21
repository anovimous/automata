package com.automata.agentcontext;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentContextItemRepository extends JpaRepository<AgentContextItem, Long> {

	List<AgentContextItem> findByContextId(Long contextId);

	boolean existsByContextIdAndRequestId(Long contextId, Long requestId);

	boolean existsByContextIdAndEqualitySetId(Long contextId, Long equalitySetId);

}
