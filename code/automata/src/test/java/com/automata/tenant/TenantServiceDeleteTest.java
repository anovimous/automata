package com.automata.tenant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.agentcontext.AgentContextRepository;
import com.automata.host.HostRepository;
import com.automata.request.RequestRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class TenantServiceDeleteTest {

	@Mock
	private TenantRepository tenantRepo;
	@Mock
	private HostRepository hostRepo;
	@Mock
	private RequestRepository requestRepo;
	@Mock
	private AgentContextRepository agentContextRepo;

	@InjectMocks
	private TenantService service;

	private Tenant tenant() {
		Tenant tenant = new Tenant();
		tenant.setId(4L);
		return tenant;
	}

	@Test
	void deleteTenant_unused_isDeleted() {
		Tenant tenant = tenant();
		when(tenantRepo.findById(4L)).thenReturn(Optional.of(tenant));
		when(requestRepo.existsByTenant(tenant)).thenReturn(false);
		when(agentContextRepo.existsByTenantsId(4L)).thenReturn(false);

		service.deleteTenant(4L);

		verify(tenantRepo).delete(tenant);
	}

	@Test
	void deleteTenant_usedByARequest_isRefused() {
		Tenant tenant = tenant();
		when(tenantRepo.findById(4L)).thenReturn(Optional.of(tenant));
		when(requestRepo.existsByTenant(tenant)).thenReturn(true);

		assertThrows(IllegalArgumentException.class, () -> service.deleteTenant(4L));

		verify(tenantRepo, never()).delete(any());
	}

	@Test
	void deleteTenant_partOfAnAgentContext_isRefused() {
		Tenant tenant = tenant();
		when(tenantRepo.findById(4L)).thenReturn(Optional.of(tenant));
		when(requestRepo.existsByTenant(tenant)).thenReturn(false);
		when(agentContextRepo.existsByTenantsId(4L)).thenReturn(true);

		assertThrows(IllegalArgumentException.class, () -> service.deleteTenant(4L));

		verify(tenantRepo, never()).delete(any());
	}

	@Test
	void deleteTenant_unknown_isNotFound() {
		when(tenantRepo.findById(4L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.deleteTenant(4L));
	}

}
