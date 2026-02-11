package com.automata.tenant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.automata.host.Host;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

	Page<Tenant> findByHost(Host host, Pageable pageable);

}
