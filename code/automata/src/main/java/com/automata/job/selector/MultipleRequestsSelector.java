package com.automata.job.selector;

import java.util.Set;

import com.automata.job.selector.matcher.RequestsHighLevelMatcher;
import com.automata.job.selector.matcher.RequestsLowLevelMatcher;

public class MultipleRequestsSelector {

	private Set<Long> ids;

	private RequestsHighLevelMatcher highLevelMatcher;

	private RequestsLowLevelMatcher lowLevelMatcher;

}
