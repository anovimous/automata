package com.automata.tenant.authentication;

import java.util.Optional;

public record AuthenticationPatchRequest(Optional<StaticAuthData> authData) {

}
