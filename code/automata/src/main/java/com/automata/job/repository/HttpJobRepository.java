package com.automata.job.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.automata.job.domain.model.HttpJob;

@Repository
public interface HttpJobRepository extends JpaRepository<HttpJob, Long> {

}
