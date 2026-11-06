package com.automata.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TenantCreationRequest(@NotBlank String name, @NotBlank String email, @NotNull Long hostId) {

}
