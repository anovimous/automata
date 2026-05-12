package com.automata.job.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.NarrowJobTargetRequest;
import com.automata.job.domain.valueobject.RequestInternalDto;

@Repository
public interface NarrowJobTargetRequestRepository extends JpaRepository<NarrowJobTargetRequest, Long> {

	@Query(value = """
			SELECT new com.automata.job.domain.valueobject.RequestInternalDto(
			    r.targetRequest.id,
			    r.targetRequest.method,
			 r.targetRequest.computatedPath,
			 r.targetRequest.extension,
			 r.targetRequest.version,
			 r.targetRequest.numberOfProperties,
			 r.targetRequest.contentType,
			 r.targetRequest.source
			)
			FROM NarrowJobTargetRequest r
			WHERE r.job = :job
			""", countQuery = "SELECT COUNT(r) FROM NarrowJobTargetRequest r WHERE r.job = :job")
	Page<RequestInternalDto> getRequestDtosByJob(@Param("job") NarrowHttpJob job, Pageable pageable);
	
	boolean existsByTargetRequestId(Long requestId);

}
