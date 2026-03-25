package com.automata.job.selector.result;

import java.util.Set;

import lombok.Builder;

@Builder
public record TargetSelectionResult(Long requestId, Set<Long> requestsIds, Long hostId, Set<Long> hostsIds) {

}
