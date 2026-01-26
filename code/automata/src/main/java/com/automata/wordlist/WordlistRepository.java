package com.automata.wordlist;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.automata.vulnerability.Vulnerability;

@Repository
public interface WordlistRepository extends JpaRepository<Wordlist, Long> {

	Page<Wordlist> findByNameContainingIgnoreCase(String name, Pageable pageable);

	Page<Wordlist> findByNameContainingIgnoreCaseAndVulnerabilities(String name, Vulnerability vuln, Pageable pageable);

}
