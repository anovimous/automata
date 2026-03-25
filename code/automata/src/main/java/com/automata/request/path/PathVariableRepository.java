package com.automata.request.path;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.request.common.dto.PathVariableInternalDto;

@Repository
public interface PathVariableRepository extends JpaRepository<PathVariable, Long> {

	@Query("""
			SELECT new com.automata.request.common.dto.PathVariableInternalDto(
			    p.index,
			    p.value,
			    p.request.id
			)
			FROM PathVariable p
			WHERE p.request.id IN :requestsIds
			""")
	List<PathVariableInternalDto> getPathVariableDtosOfRequests(@Param("requestsIds") List<Long> requestsIds);

}
