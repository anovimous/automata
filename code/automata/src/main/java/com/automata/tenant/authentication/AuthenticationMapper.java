package com.automata.tenant.authentication;

public abstract class AuthenticationMapper {

	public static AuthenticationDto toAuthenticationDto(Authentication authentication) {

		return AuthenticationDto.builder().id(authentication.getId()).creationDate(authentication.getCreationDate())
				.authData(authentication.getAuthData())
				.isDynamicPopulationAvailable(authentication.isDynamicPopulationAvailable())
				.code(authentication.getCode()).tenantId(null).build();

	}

	public static Authentication toAuthentication(AuthenticationCreationRequest request) {

		return Authentication.builder().authData(request.authData())
				.isDynamicPopulationAvailable(request.isDynamicPopulationAvailable()).code(request.code()).build();

	}

}
