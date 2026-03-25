package com.automata.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HttpJobRepository extends JpaRepository<HttpJob, Long> {

}
