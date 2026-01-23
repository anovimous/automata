package com.automata.program;

import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {

	Page<Program> findByNameContainingIgnoreCase(String query, Pageable pageable);
	
}
