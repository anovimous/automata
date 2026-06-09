package com.automata.tenant.authentication;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.automata.tenant.Tenant;
import com.automata.tenant.TenantRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

	private final AuthenticationRepository authRepo;

	private final TenantRepository tenantRepo;

	@Transactional(readOnly = true)
	public Authentication getAuthenticationOfTenant(Long tenantId) {

		Tenant tenant = tenantRepo.findById(tenantId)
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		return tenant.getAuthentication();

	}

	@Transactional
	public Authentication createAuthentication(Authentication authentication, Long tenantId) {

		Tenant tenant = tenantRepo.findById(tenantId)
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		if (tenant.getAuthentication() != null)
			authRepo.delete(tenant.getAuthentication());

		Authentication persistedAuth = authRepo.save(authentication);

		tenant.setAuthentication(persistedAuth);

		tenantRepo.save(tenant);
		
		persistedAuth.setTenant(tenant);

		return persistedAuth;

	}

	@Transactional
	public Authentication patchAuthentication(AuthenticationPatchRequest patchRequest, Long tenantId) {

		Tenant tenant = tenantRepo.findById(tenantId)
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		Authentication authentication = tenant.getAuthentication();

		if (authentication == null)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Authentication not found for this tenant");

		patchRequest.authData().ifPresent(authentication::setAuthData);

		patchRequest.isDynamicPopulationAvailable().ifPresent(authentication::setDynamicPopulationAvailable);

		patchRequest.code().ifPresent(authentication::setCode);

		authRepo.save(authentication);

		return authentication;

	}

	@Transactional
	public void deleteAuthentication(Long tenantId) {

		Tenant tenant = tenantRepo.findById(tenantId)
				.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));

		Authentication authentication = tenant.getAuthentication();

		if (authentication != null)
			authRepo.delete(authentication);

	}
}
