package com.automata.tenant;

public record TenantCreationRequest(String name, String email, Long hostId) {

}
