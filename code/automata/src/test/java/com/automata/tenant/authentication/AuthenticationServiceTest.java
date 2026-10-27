package com.automata.tenant.authentication;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.tenant.Tenant;
import com.automata.tenant.TenantRepository;
import com.automata.testsupport.KnownBug;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

	@Mock
	AuthenticationRepository authRepo;

	@Mock
	TenantRepository tenantRepo;

	@InjectMocks
	AuthenticationService service;

	private static Authentication auth() {
		return Authentication.builder().authData(new StaticAuthData()).build();
	}

	@Test
	void getReturnsTheAuthenticationOfTheTenant() {
		Authentication authentication = auth();
		Tenant tenant = new Tenant();
		tenant.setAuthentication(authentication);
		when(tenantRepo.findById(1L)).thenReturn(Optional.of(tenant));

		assertSame(authentication, service.getAuthenticationOfTenant(1L));
	}

	@Test
	void getOfUnknownTenantIsNotFound() {
		when(tenantRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getAuthenticationOfTenant(9L));
	}

	@Test
	void getOfATenantWithoutAuthenticationReturnsNull() {
		when(tenantRepo.findById(1L)).thenReturn(Optional.of(new Tenant()));

		assertNull(service.getAuthenticationOfTenant(1L));
	}

	@Test
	void createSavesLinksBothSidesAndReturnsTheAuthentication() {
		Tenant tenant = new Tenant();
		Authentication toSave = auth();
		when(tenantRepo.findById(1L)).thenReturn(Optional.of(tenant));
		when(authRepo.save(toSave)).thenReturn(toSave);

		Authentication created = service.createAuthentication(toSave, 1L);

		assertSame(toSave, tenant.getAuthentication());
		assertSame(tenant, created.getTenant());
		verify(authRepo, never()).delete(any());
		verify(tenantRepo).save(tenant);
	}

	@Test
	void createReplacesTheExistingAuthentication() {
		Authentication old = auth();
		Tenant tenant = new Tenant();
		tenant.setAuthentication(old);
		Authentication fresh = auth();
		when(tenantRepo.findById(1L)).thenReturn(Optional.of(tenant));
		when(authRepo.save(fresh)).thenReturn(fresh);

		service.createAuthentication(fresh, 1L);

		verify(authRepo).delete(old);
		assertSame(fresh, tenant.getAuthentication());
	}

	@Test
	void createOnUnknownTenantIsNotFoundAndSavesNothing() {
		when(tenantRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.createAuthentication(auth(), 9L));
		verify(authRepo, never()).save(any());
	}

	@Test
	void deleteOfUnknownTenantIsNotFound() {
		when(tenantRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.deleteAuthentication(9L));
	}

	@Test
	void deleteOfATenantWithoutAuthenticationDoesNothing() {
		when(tenantRepo.findById(1L)).thenReturn(Optional.of(new Tenant()));

		service.deleteAuthentication(1L);

		verify(authRepo, never()).delete(any());
	}

	@Test
	@KnownBug("BUG-010")
	void deleteUnlinksTheAuthenticationFromTheTenantBeforeRemovingIt() {
		Authentication authentication = auth();
		Tenant tenant = new Tenant();
		tenant.setAuthentication(authentication);
		when(tenantRepo.findById(1L)).thenReturn(Optional.of(tenant));

		service.deleteAuthentication(1L);

		assertNull(tenant.getAuthentication());
		InOrder order = inOrder(authRepo);
		order.verify(authRepo).delete(authentication);
	}
}
