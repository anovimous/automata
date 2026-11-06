package com.automata.job.domain.model.embedded;

import java.util.List;

import com.automata.job.domain.policy.LogicalOperation;

public class RequestPredicateGroup extends RequestPredicate {

	List<RequestPredicate> predicates;

	LogicalOperation innerLogicalOperator;

	LogicalOperation relationToNextNode;

}
