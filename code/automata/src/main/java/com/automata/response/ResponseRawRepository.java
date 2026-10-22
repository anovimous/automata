package com.automata.response;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// The id of a ResponseRaw is the id of its Response
@Repository
public interface ResponseRawRepository extends JpaRepository<ResponseRaw, Long> {

}
