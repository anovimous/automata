package com.automata.host.common.dto;

import com.automata.host.common.enums.Scope;

public record HostCreationRequest(String host, Scope scope, Integer level, Integer hostRateLimit, Integer shortRateLimit, Integer longRateLimit, Long programId) {

}
