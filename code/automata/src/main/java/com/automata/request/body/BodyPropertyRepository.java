package com.automata.request.body;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BodyPropertyRepository extends JpaRepository<BodyProperty, Long> {

}
