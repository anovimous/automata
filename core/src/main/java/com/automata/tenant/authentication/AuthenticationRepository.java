package com.automata.tenant.authentication;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.automata.tenant.Tenant;

@Repository
public interface AuthenticationRepository extends JpaRepository<Authentication, Long> {

	Optional<Authentication> findByTenant(Tenant tenant);

}
