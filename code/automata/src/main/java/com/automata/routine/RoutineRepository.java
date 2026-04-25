package com.automata.routine;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.automata.vulnerability.Vulnerability;

@Repository
public interface RoutineRepository extends JpaRepository<Routine, Long> {

	Page<Routine> findByKeyContainingIgnoreCase(String key, Pageable pageable);

	Page<Routine> findByKeyContainingIgnoreCaseAndVulnerability(String key, Vulnerability vulnerability,
			Pageable pageable);

}
