package com.automata.request.comparator;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.automata.request.comparator.common.enums.Schema;

@Repository
public interface ModifierRepository extends JpaRepository<Modifier, Long> {

	Page<Modifier> findByKeyContainingIgnoreCaseAndSchema(String query, Schema schema, Pageable pageable);

	Page<Modifier> findByKeyContainingIgnoreCase(String query, Pageable pageable);

}
