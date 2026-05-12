package com.automata.tenant;

import org.springframework.data.domain.Page;

import com.automata.tenant.common.dto.TenantSummaryResponseDto;

public abstract class TenantMapper {

	public static TenantDetailedResponseDto toDetailedResponse(Tenant tenant) {

		return TenantDetailedResponseDto.builder().id(tenant.getId()).creationDate(tenant.getCreationDate())
				.name(tenant.getName()).email(tenant.getEmail()).hostId(tenant.getHost().getId()).build();

	}

	public static TenantSummaryResponseDto toSummaryResponse(Tenant tenant) {

		return TenantSummaryResponseDto.builder().id(tenant.getId()).creationDate(tenant.getCreationDate())
				.name(tenant.getName()).build();

	}

	public static Tenant toTenant(TenantCreationRequest request) {

		return Tenant.builder().name(request.name()).email(request.email()).build();

	}

	public static Page<TenantSummaryResponseDto> toSummaryResponse(Page<Tenant> tenants) {
		return tenants.map(TenantMapper::toSummaryResponse);
	}

}
