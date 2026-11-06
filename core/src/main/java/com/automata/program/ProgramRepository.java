package com.automata.program;

import org.springframework.stereotype.Repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {

	Page<Program> findByNameContainingIgnoreCase(String query, Pageable pageable);

	@Query("SELECT p FROM Program p LEFT JOIN FETCH p.outOfScopeVulns WHERE p.id = :id")
	Optional<Program> findByIdWithVulns(@Param("id") Long id);

}
