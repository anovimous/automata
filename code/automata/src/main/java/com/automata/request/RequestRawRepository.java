package com.automata.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// The id of a RequestRaw is the id of its Request
@Repository
public interface RequestRawRepository extends JpaRepository<RequestRaw, Long> {

}
