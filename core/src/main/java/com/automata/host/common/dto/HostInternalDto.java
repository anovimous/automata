package com.automata.host.common.dto;

import com.automata.host.common.enums.Scope;

import lombok.Builder;

@Builder
public record HostInternalDto(Long hostId, String host, Scope scope) {

}
