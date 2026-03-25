package com.automata.job;

import java.util.List;

import lombok.Builder;

@Builder
public record JobTargetData(List<RequestData> requests, List<HostData> hosts) {
}
