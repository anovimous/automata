package com.automata.tenant.authentication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.automata.tenant.Tenant;
import com.automata.tenant.TenantRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

	private final AuthenticationRepository authRepo;

	private final TenantRepository tenantRepo;

	public Authentication getAuthenticationById(Long authenticationId, Long tenantId) {

		Tenant tenant = tenantRepo.findById(tenantId)
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		Authentication auth = authRepo.findById(authenticationId)
				.orElseThrow(() -> new EntityNotFoundException("Authentication not found"));

		if (auth.getTenant().getId() != tenant.getId())
			throw new IllegalArgumentException("Authentication does not belong to the specified tenant");

		return auth;

	}

	public Authentication createAuthentication(Authentication authentication, Long tenantId) {

		Tenant tenant = tenantRepo.findById(tenantId)
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		authentication.setTenant(tenant);

		authentication.setHost(tenant.getHost());

		return authRepo.save(authentication);

	}

	public Page<Authentication> getTenantAuthenticationsPage(Long tenantId, Pageable pageable) {

		Tenant tenant = tenantRepo.findById(tenantId)
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		return authRepo.findByTenant(tenant, pageable);

	}

	public Authentication patchAuthentication(Long authenticationId, AuthenticationPatchRequest patchRequest,
			Long tenantId) {

		Tenant tenant = tenantRepo.findById(tenantId)
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		Authentication authentication = authRepo.findById(authenticationId)
				.orElseThrow(() -> new EntityNotFoundException("Authentication not found"));

		if (authentication.getTenant().getId() != tenant.getId())
			throw new IllegalArgumentException("Authentication does not belong to the specified tenant");

		patchRequest.authData().ifPresent(authentication::setAuthData);

		patchRequest.isDynamicPopulationAvailable().ifPresent(authentication::setDynamicPopulationAvailable);

		patchRequest.code().ifPresent(authentication::setCode);

		authRepo.save(authentication);

		return authentication;

	}

	public void deleteAuthentication(Long authenticationId, Long tenantId) {

		Tenant tenant = tenantRepo.findById(tenantId)
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		Authentication authentication = authRepo.findById(authenticationId)
				.orElseThrow(() -> new EntityNotFoundException("Authentication not found"));

		if (authentication.getTenant().getId() != tenant.getId())
			throw new IllegalArgumentException("Authentication does not belong to the specified tenant");

		authRepo.delete(authentication);

	}
}
