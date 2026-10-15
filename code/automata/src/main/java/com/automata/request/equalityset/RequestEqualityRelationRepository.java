package com.automata.request.equalityset;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.request.Request;

@Repository
public interface RequestEqualityRelationRepository extends JpaRepository<RequestEqualityRelation, Long> {

	@Query("""
					SELECT new com.automata.request.equalityset.RequestEqualityRelationSummaryInternalDto(r.request.id, r.set.id)
					 FROM RequestEqualityRelation
			r WHERE r.set.equalization.id = :equalizationId
			""")
	List<RequestEqualityRelationSummaryInternalDto> findRequestEqualitySetPairs(
			@Param("equalizationId") Long equalizationId);

	@Query("SELECT rel.request FROM RequestEqualityRelation rel WHERE rel.set = :set")
	Page<Request> findAllInSet(RequestEqualitySet set, Pageable pageable);

}
