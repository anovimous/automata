package com.automata.job.domain.valueobject;

import com.automata.host.common.enums.Scope;

public record HostData(Long hostId, String host, Scope scope) {

}
