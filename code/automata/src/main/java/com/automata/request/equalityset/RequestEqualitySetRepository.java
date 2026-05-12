package com.automata.request.equalityset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestEqualitySetRepository extends JpaRepository<RequestEqualitySet, Long> {

	boolean existsByRequests_Id(Long requestId);

}
