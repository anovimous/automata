package com.automata.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import com.automata.host.Host;
import com.automata.tenant.TenantDetailedResponseDto;
import com.automata.tenant.common.dto.TenantSummaryResponseDto;

class TenantMapperTest {

	private static Tenant tenant() {
		return Tenant.builder().id(3L).creationDate(LocalDate.of(2026, 3, 4)).name("alice").email("a@x.test")
				.host(Host.of(8L)).build();
	}

	@Test
	void detailedResponseCarriesTheHostId() {
		TenantDetailedResponseDto response = TenantMapper.toDetailedResponse(tenant());

		assertEquals(3L, response.id());
		assertEquals("alice", response.name());
		assertEquals("a@x.test", response.email());
		assertEquals(8L, response.hostId());
		assertEquals(LocalDate.of(2026, 3, 4), response.creationDate());
	}

	@Test
	void summaryResponseHasNoEmailOrHost() {
		TenantSummaryResponseDto response = TenantMapper.toSummaryResponse(tenant());

		assertEquals(new TenantSummaryResponseDto(3L, LocalDate.of(2026, 3, 4), "alice"), response);
	}

	@Test
	void toTenantTakesNameAndEmailButNotTheHost() {
		Tenant tenant = TenantMapper.toTenant(new TenantCreationRequest("bob", "b@x.test", 8L));

		assertEquals("bob", tenant.getName());
		assertEquals("b@x.test", tenant.getEmail());
		assertNull(tenant.getHost());
		assertNull(tenant.getId());
	}

	@Test
	void summaryPageMapsEveryTenant() {
		var page = TenantMapper.toSummaryResponse(new PageImpl<>(List.of(tenant(), tenant())));

		assertEquals(2, page.getContent().size());
	}
}
