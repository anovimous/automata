package com.automata.tenant.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class AuthenticationMapperTest {

	private StaticAuthData authData() {
		return StaticAuthData.builder().data(StaticData.builder()
				.headers(java.util.List.of(new AuthNameValuePair("Authorization", "Bearer x"))).build()).build();
	}

	@Test
	void toAuthenticationDto_exposesTheStaticAuthData() {
		StaticAuthData authData = authData();
		Authentication authentication = Authentication.builder().id(4L).creationDate(LocalDate.of(2026, 10, 22))
				.authData(authData).build();

		AuthenticationDto dto = AuthenticationMapper.toAuthenticationDto(authentication);

		assertEquals(4L, dto.id());
		assertEquals(LocalDate.of(2026, 10, 22), dto.creationDate());
		assertSame(authData, dto.authData());
	}

	@Test
	void toAuthenticationDto_carriesTheIdOfTheTenant() {
		com.automata.tenant.Tenant tenant = new com.automata.tenant.Tenant();
		tenant.setId(8L);
		Authentication authentication = Authentication.builder().id(4L).authData(authData()).tenant(tenant).build();

		assertEquals(8L, AuthenticationMapper.toAuthenticationDto(authentication).tenantId());
	}

	@Test
	void toAuthenticationDto_hasNoTenantIdWhenTheAuthenticationKnowsNoTenant() {
		Authentication authentication = Authentication.builder().id(4L).authData(authData()).build();

		org.junit.jupiter.api.Assertions.assertNull(AuthenticationMapper.toAuthenticationDto(authentication).tenantId());
	}

	@Test
	void toAuthentication_takesTheAuthDataOfTheCreationRequest() {
		StaticAuthData authData = authData();

		Authentication authentication = AuthenticationMapper
				.toAuthentication(new AuthenticationCreationRequest(authData));

		assertSame(authData, authentication.getAuthData());
	}

}
