package com.automata.request.comparator.hash.strategy;

import com.automata.request.Request;
import com.automata.request.comparator.hash.HashTarget;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;

public interface HashStrategy {

	HashTarget supports();

	HashCode hashStrategyTarget(Request request, HashFunction function);

}
