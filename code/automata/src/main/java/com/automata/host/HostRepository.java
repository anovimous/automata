package com.automata.host;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.automata.program.Program;

@Repository
public interface HostRepository extends JpaRepository<Host, Long> {

	Page<Host> findByHostContainingIgnoreCase(String query, Pageable pageable);

	Page<Host> findByHostContainingIgnoreCaseAndProgram(String name, Program program, Pageable pageable);

}
