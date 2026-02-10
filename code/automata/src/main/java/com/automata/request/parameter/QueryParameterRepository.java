package com.automata.request.parameter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryParameterRepository extends JpaRepository<QueryParameter, Long> {

}
