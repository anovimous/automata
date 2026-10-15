package com.automata.request.equalityset;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.automata.host.Host;

@Repository
public interface EqualizationRepository extends JpaRepository<Equalization, Long> {

	@Query("SELECT e FROM Equalization e WHERE e.host = :host")
	List<Equalization> findAllByHost(@Param(value = "host") Host host);

}
