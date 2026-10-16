package com.automata.job.domain.valueobject;

import java.util.Set;

import lombok.Builder;

/**
 * For a single request or a single host selector, hostId is the host of the
 * selected target.
 */
@Builder
public record TargetSelectionResult(Long requestId, Set<Long> requestsIds, Long hostId, Set<Long> hostsIds) {

}
