package com.automata.tenant.authentication;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.automata.tenant.Tenant;
import com.automata.tenant.TenantRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class AuthenticationServicePatchTest {

	@Mock
	private AuthenticationRepository authRepo;
	@Mock
	private TenantRepository tenantRepo;

	@InjectMocks
	private AuthenticationService service;

	@Test
	void patchAuthentication_replacesTheAuthData() {
		Authentication authentication = Authentication.builder().authData(new StaticAuthData()).build();
		Tenant tenant = new Tenant();
		tenant.setAuthentication(authentication);
		when(tenantRepo.findById(3L)).thenReturn(Optional.of(tenant));
		StaticAuthData newData = new StaticAuthData();

		service.patchAuthentication(new AuthenticationPatchRequest(Optional.of(newData)), 3L);

		assertSame(newData, authentication.getAuthData());
		verify(authRepo).save(authentication);
	}

	@Test
	void patchAuthentication_emptyPatch_keepsTheAuthData() {
		StaticAuthData original = new StaticAuthData();
		Authentication authentication = Authentication.builder().authData(original).build();
		Tenant tenant = new Tenant();
		tenant.setAuthentication(authentication);
		when(tenantRepo.findById(3L)).thenReturn(Optional.of(tenant));

		service.patchAuthentication(new AuthenticationPatchRequest(Optional.empty()), 3L);

		assertSame(original, authentication.getAuthData());
	}

	@Test
	void patchAuthentication_tenantWithoutAuthentication_isNotFound() {
		when(tenantRepo.findById(3L)).thenReturn(Optional.of(new Tenant()));

		assertThrows(ResponseStatusException.class,
				() -> service.patchAuthentication(new AuthenticationPatchRequest(Optional.empty()), 3L));
	}

	@Test
	void patchAuthentication_unknownTenant_isNotFound() {
		when(tenantRepo.findById(3L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class,
				() -> service.patchAuthentication(new AuthenticationPatchRequest(Optional.empty()), 3L));
	}

}
