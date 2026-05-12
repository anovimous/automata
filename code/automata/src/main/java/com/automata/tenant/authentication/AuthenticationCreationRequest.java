package com.automata.tenant.authentication;

public record AuthenticationCreationRequest(StaticAuthData authData, boolean isDynamicPopulationAvailable,
		DynamicCode code) {

}
