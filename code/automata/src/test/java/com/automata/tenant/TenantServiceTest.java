package com.automata.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.automata.agentcontext.AgentContextRepository;
import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.request.RequestRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

	@Mock
	TenantRepository tenantRepo;

	@Mock
	HostRepository hostRepo;

	@Mock
	RequestRepository requestRepo;

	@Mock
	AgentContextRepository agentContextRepo;

	@InjectMocks
	TenantService service;

	private static Tenant tenant(String name, String email) {
		return Tenant.builder().id(1L).name(name).email(email).build();
	}

	// ---- get ----

	@Test
	void getReturnsTheTenant() {
		Tenant tenant = tenant("alice", "a@x.test");
		when(tenantRepo.findById(1L)).thenReturn(Optional.of(tenant));

		assertSame(tenant, service.getTenantById(1L));
	}

	@Test
	void getOfUnknownTenantIsNotFound() {
		when(tenantRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getTenantById(9L));
	}

	// ---- create ----

	@Test
	void createAttachesTheHostAndSaves() {
		Host host = Host.of(4L);
		Tenant toSave = tenant("alice", "a@x.test");
		when(hostRepo.findById(4L)).thenReturn(Optional.of(host));
		when(tenantRepo.save(toSave)).thenReturn(toSave);

		Tenant created = service.createTenant(toSave, 4L);

		assertSame(host, created.getHost());
	}

	@Test
	void createOnUnknownHostIsNotFoundAndSavesNothing() {
		when(hostRepo.findById(9L)).thenReturn(Optional.empty());

		EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
				() -> service.createTenant(tenant("alice", "a@x.test"), 9L));
		assertEquals("Host not found", ex.getMessage());
		verify(tenantRepo, never()).save(any());
	}

	// ---- list ----

	@Test
	void listReturnsTheTenantsOfTheHost() {
		Host host = Host.of(4L);
		Pageable pageable = PageRequest.of(0, 10);
		Page<Tenant> page = new PageImpl<>(List.of(tenant("alice", "a@x.test")));
		when(hostRepo.findById(4L)).thenReturn(Optional.of(host));
		when(tenantRepo.findByHost(host, pageable)).thenReturn(page);

		assertSame(page, service.getHostTenantsPage(4L, pageable));
	}

	@Test
	void listOfUnknownHostIsNotFound() {
		when(hostRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getHostTenantsPage(9L, PageRequest.of(0, 10)));
	}

	// ---- patch ----

	@Test
	void patchChangesOnlyTheGivenFields() {
		Tenant tenant = tenant("alice", "a@x.test");
		when(tenantRepo.findById(1L)).thenReturn(Optional.of(tenant));

		service.patchTenant(1L, new TenantPatchRequest(Optional.of("alicia"), Optional.empty()));

		assertEquals("alicia", tenant.getName());
		assertEquals("a@x.test", tenant.getEmail());
		verify(tenantRepo).save(tenant);
	}

	@Test
	void patchChangesTheEmail() {
		Tenant tenant = tenant("alice", "a@x.test");
		when(tenantRepo.findById(1L)).thenReturn(Optional.of(tenant));

		service.patchTenant(1L, new TenantPatchRequest(Optional.empty(), Optional.of("new@x.test")));

		assertEquals("alice", tenant.getName());
		assertEquals("new@x.test", tenant.getEmail());
	}

	@Test
	void patchOfUnknownTenantIsNotFound() {
		when(tenantRepo.findById(9L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class,
				() -> service.patchTenant(9L, new TenantPatchRequest(Optional.empty(), Optional.empty())));
	}
}
