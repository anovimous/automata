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
			    b.propertyValueType,
			    b.request.id,
			    CAST(null AS Long)
			)
			FROM BodyProperty b
			WHERE b.request.id IN :requestsIds
			""")
	List<BodyPropertyInternalDto> getBodyPropertyDtosByRequestsIds(@Param("requestsIds") List<Long> requestsIds);

	@Query("""
			SELECT new com.automata.request.common.dto.BodyPropertyInternalDto(
			    b.fullPath,
			    b.value,
			    b.propertyValueType,
			    CAST(null AS Long),
			    b.response.id
			)
			FROM BodyProperty b
			WHERE b.response.id IN :ids
			""")
	List<BodyPropertyInternalDto> getBodyPropertyDtosByResponseIds(@Param("ids") List<Long> ids);

	// Export queries

	@Query("""
			SELECT DISTINCT b.fullPath
			FROM BodyProperty b
			WHERE b.request IS NOT NULL
			  AND b.request.host.id = :hostId
			""")
	List<String> findDistinctFullPathsByRequestHostId(@Param("hostId") Long hostId);

	@Query("""
			SELECT DISTINCT b.fullPath
			FROM BodyProperty b
			WHERE b.response IS NOT NULL
			  AND b.response.request.host.id = :hostId
			""")
	List<String> findDistinctFullPathsByResponseHostId(@Param("hostId") Long hostId);

	@Query("""
			SELECT DISTINCT b.fullPath
			FROM BodyProperty b
			WHERE b.request IS NOT NULL
			  AND b.request.program.id = :programId
			""")
	List<String> findDistinctFullPathsByRequestProgramId(@Param("programId") Long programId);

	@Query("""
			SELECT DISTINCT b.fullPath
			FROM BodyProperty b
			WHERE b.response IS NOT NULL
			  AND b.response.request.program.id = :programId
			""")
	List<String> findDistinctFullPathsByResponseProgramId(@Param("programId") Long programId);
}
