package com.automata.job.selector.matcher;

import java.util.List;

public class RequestPredicateGroup extends RequestPredicate {

	List<RequestPredicate> predicates;

	LogicalOperation innerLogicalOperator;

	LogicalOperation relationToNextNode;

}
