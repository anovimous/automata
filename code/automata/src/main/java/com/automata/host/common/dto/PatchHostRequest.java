package com.automata.host.common.dto;

import com.automata.host.common.enums.Scope;

public record PatchHostRequest(String host, Scope scope, Integer hostRateLimit, Integer shortRateLimit, Integer longRateLimit) {

}
