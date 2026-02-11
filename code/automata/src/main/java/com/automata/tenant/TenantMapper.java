package com.automata.tenant;

public abstract class TenantMapper {

	public static TenantResponse toTenantResponse(Tenant tenant) {

		return TenantResponse.builder().id(tenant.getId()).creationDate(tenant.getCreationDate()).name(tenant.getName())
				.email(tenant.getEmail()).hostId(tenant.getHost().getId()).build();

	}

	public static Tenant toTenant(TenantCreationRequest request) {

		return Tenant.builder().name(request.name()).email(request.email()).build();

	}

}
