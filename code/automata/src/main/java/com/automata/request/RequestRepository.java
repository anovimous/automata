package com.automata.request;

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

	boolean existsByIdAndEqualitySetsIsNotEmpty(Long requestId);

	int countByHost(Host host);

	List<Long> findIdsByHost(Host host);

	Page<Request> findByHost(Host host, Pageable ofSize);

	Page<Request> findByIdIn(List<Long> ids, Pageable ofSize);

	boolean existsByTenant(Tenant tenant);

	@Query("""
			    select r.id
			    from Request r
			    join r.equalitySets e
			    where e.id = :id
			    order by r.insertionDate asc
			""")
	Page<Long> findRequestIdsPageByEqualitySetId(@Param("id") Long equalitySetId, Pageable pageable);

}
