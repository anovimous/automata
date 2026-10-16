package com.automata.job.domain.valueobject;

import java.util.Set;

/**
 * What a target selector resolved to. There is one kind per selector type, each
 * holding only what that selection produces.
 */
public sealed interface TargetSelectionResult {

	record SingleHost(Long hostId) implements TargetSelectionResult {
	}

	/**
	 * @param hostId the host of the selected request
	 */
	record SingleRequest(Long requestId, Long hostId) implements TargetSelectionResult {
	}

	record MultipleHosts(Set<Long> hostsIds) implements TargetSelectionResult {
	}

	record MultipleRequests(Set<Long> requestsIds) implements TargetSelectionResult {
	}

}
