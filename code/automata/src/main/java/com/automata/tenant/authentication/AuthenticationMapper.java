package com.automata.tenant.authentication;

public abstract class AuthenticationMapper {

	public static AuthenticationDto toAuthenticationDto(Authentication authentication) {

		return AuthenticationDto.builder().id(authentication.getId()).creationDate(authentication.getCreationDate())
				.authData(authentication.getAuthData()).tenantId(null).build();

	}

	public static Authentication toAuthentication(AuthenticationCreationRequest request) {

		return Authentication.builder().authData(request.authData()).build();

	}

}
