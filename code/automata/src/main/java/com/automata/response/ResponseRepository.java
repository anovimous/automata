package com.automata.response;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.request.Request;

@Repository
public interface ResponseRepository extends JpaSpecificationExecutor<Response>, JpaRepository<Response, Long> {

	Optional<Response> findByRequest(Request request);

	@Query("SELECT r FROM Response r WHERE r.request.id = :requestId")
	Optional<Response> getByRequestId(@Param(value = "requestId") Long requestId);

}
