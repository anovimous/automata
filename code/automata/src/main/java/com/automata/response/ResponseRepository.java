package com.automata.response;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.automata.request.Request;

@Repository
public interface ResponseRepository extends JpaSpecificationExecutor<Response>,JpaRepository<Response, Long> {

	Optional<Response> findByRequest(Request request);

}
