package com.automata.tenant;

import java.util.Optional;

public record TenantPatchRequest(Optional<String> name, Optional<String> email) {

}
