package com.automata.job.domain.model.embedded;

import com.automata.job.domain.policy.PredicateOperation;

public abstract class RequestPredicate {

	private PredicateTargetComponent component;

	private PredicateOperation operation;

	private String value;
}
