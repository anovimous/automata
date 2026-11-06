package com.automata.request.parameter;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.request.common.dto.QueryParameterInternalDto;

@Repository
public interface QueryParameterRepository extends JpaRepository<QueryParameter, Long> {

	@Query("""
			SELECT new com.automata.request.common.dto.QueryParameterInternalDto(
			    q.parameter,
			    q.value,
			    q.request.id
			)
			FROM QueryParameter q
			WHERE q.request.id IN :requestsIds
			""")
	List<QueryParameterInternalDto> getQueryParameterDtosByRequestsIds(@Param("requestsIds") List<Long> requestsIds);

}
