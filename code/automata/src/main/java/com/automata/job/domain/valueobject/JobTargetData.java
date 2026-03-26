package com.automata.job.domain.valueobject;

import java.util.List;

import lombok.Builder;

@Builder
public record JobTargetData(List<RequestData> requests, List<HostData> hosts) {
}
