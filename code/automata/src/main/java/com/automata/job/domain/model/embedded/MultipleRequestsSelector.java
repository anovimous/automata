package com.automata.job.domain.model.embedded;

import java.util.Set;

public class MultipleRequestsSelector {

	private Set<Long> ids;

	private RequestsHighLevelMatcher highLevelMatcher;

	private RequestsLowLevelMatcher lowLevelMatcher;

}
