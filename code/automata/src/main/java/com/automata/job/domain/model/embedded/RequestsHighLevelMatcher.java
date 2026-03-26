package com.automata.job.domain.model.embedded;

import java.util.Optional;
import java.util.Set;

public class RequestsHighLevelMatcher {

	private Long hostId;

	private Optional<Set<Long>> tenantsIds;

	private Optional<Set<Long>> equalitySetsIds;

}
