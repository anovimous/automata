package com.automata.request.equalityset;

import java.util.Collection;
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

	List<RequestEqualityRelation> findAllBySet(RequestEqualitySet set);

	boolean existsBySetAndRequestId(RequestEqualitySet set, Long requestId);

	@Query("SELECT rel.request FROM RequestEqualityRelation rel WHERE rel.set = :set ORDER BY rel.request.insertionDate DESC, rel.request.id DESC LIMIT 1")
	Request findTopBySetOrderByRequestInsertionDateDesc(RequestEqualitySet set);

	@Query("SELECT rel.request FROM RequestEqualityRelation rel WHERE rel.set.id = :setId ORDER BY rel.request.insertionDate DESC, rel.request.id DESC")
	Page<Request> findRequestsInSet(@Param("setId") Long setId, Pageable pageable);

	// The pairs (set, request) of the given requests inside the given sets, smallest set id first
	@Query("""
			SELECT new com.automata.request.equalityset.RequestEqualityRelationSummaryInternalDto(rel.set.id, rel.request.id)
			 FROM RequestEqualityRelation rel WHERE rel.request.id IN :requestIds AND rel.set.id IN :setIds
			ORDER BY rel.set.id
			""")
	List<RequestEqualityRelationSummaryInternalDto> findSetIdsOfRequests(@Param("requestIds") Collection<Long> requestIds,
			@Param("setIds") Collection<Long> setIds);

}
