package com.automata.tenant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.request.RequestRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantService {

	private final TenantRepository tenantRepo;

	private final HostRepository hostRepo;

	private final RequestRepository requestRepo;

	public Tenant getTenantById(Long tenantId) {

		return tenantRepo.findById(tenantId).orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

	}

	public Tenant createTenant(Tenant tenant, Long hostId) {

		Host host = hostRepo.findById(hostId).orElseThrow(() -> new EntityNotFoundException("Host not found"));

		tenant.setHost(host);

		return tenantRepo.save(tenant);

	}

	public Page<Tenant> getHostTenantsPage(Long hostId, Pageable pageable) {

		Host host = hostRepo.findById(hostId).orElseThrow(() -> new EntityNotFoundException("Host not found"));

		return tenantRepo.findByHost(host, pageable);

	}

	public Tenant patchTenant(Long tenantId, TenantPatchRequest patchRequest) {

		Tenant tenant = tenantRepo.findById(tenantId)
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		patchRequest.name().ifPresent(tenant::setName);

		patchRequest.email().ifPresent(tenant::setEmail);

		tenantRepo.save(tenant);

		return tenant;

	}

	public void deleteTenant(Long tenantId) {

		Tenant tenant = tenantRepo.findById(tenantId)
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		if (requestRepo.existsByTenant(tenant))
			throw new IllegalArgumentException("Tenant can't be deleted if it is associated with any request");

		tenantRepo.delete(tenant);

	}

}
