package com.automata.tenant.authentication;

import jakarta.validation.constraints.NotNull;

public record AuthenticationCreationRequest(@NotNull StaticAuthData authData) {

}
