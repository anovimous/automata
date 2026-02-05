package com.automata.request.path;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PathVariableRepository extends JpaRepository<PathVariable, Long> {

}
