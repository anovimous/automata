package com.automata.tenant.authentication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.automata.tenant.Tenant;

@Repository
public interface AuthenticationRepository extends JpaRepository<Authentication, Long> {

	Page<Authentication> findByTenant(Tenant tenant, Pageable pageable);

	Authentication findByTenant(Tenant tenant);

}
