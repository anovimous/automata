package com.automata.request;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.host.Host;
import com.automata.tenant.Tenant;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long>, JpaSpecificationExecutor<Request> {

	int countByHost(Host host);

	@Query("SELECT r.id FROM Request r WHERE r.host = :host")
	HashSet<Long> findIdsByHost(@Param(value = "host") Host host);

	Page<Request> findByHost(Host host, Pageable ofSize);

	Page<Request> findByIdIn(List<Long> ids, Pageable ofSize);

	// The requests of an agent context: the ones of its REQUEST items and every member of its
	// EQUALITY_SET items, newest first
	@Query("""
			SELECT r FROM Request r WHERE r.id IN :requestIds
			 OR r.id IN (SELECT rel.request.id FROM RequestEqualityRelation rel WHERE rel.set.id IN :setIds)
			ORDER BY r.insertionDate DESC, r.id DESC
			""")
	Page<Request> findInContext(@Param("requestIds") Collection<Long> requestIds,
			@Param("setIds") Collection<Long> setIds, Pageable pageable);

	boolean existsByTenant(Tenant tenant);

	@Query("SELECT new com.automata.request.RequestTenantPair(r.id, r.tenant.id) FROM Request r WHERE r.id IN :ids AND r.tenant IS NOT NULL")
	List<RequestTenantPair> findTenantPairsByIdIn(@Param("ids") Collection<Long> ids);

//	@Query("""
//			    select r.id
//			    from Request r
//			    join r.equalitySets e
//			    where e.id = :id
//			    order by r.insertionDate asc
//			""")
//	Page<Long> findRequestIdsPageByEqualitySetId(@Param("id") Long equalitySetId, Pageable pageable);

}
