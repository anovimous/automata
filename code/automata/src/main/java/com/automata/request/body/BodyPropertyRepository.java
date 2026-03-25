package com.automata.request.body;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.request.common.dto.BodyPropertyInternalDto;

@Repository
public interface BodyPropertyRepository extends JpaRepository<BodyProperty, Long> {

	@Query("""
			SELECT new com.automata.request.common.dto.BodyPropertyInternalDto(
			    b.fullPath,
			    b.value,
			    b.request.id
			)
			FROM BodyProperty b
			WHERE b.request.id IN :requestsIds
			""")
	List<BodyPropertyInternalDto> getBodyPropertyDtosByRequestsIds(@Param("requestsIds") List<Long> requestsIds);

}
