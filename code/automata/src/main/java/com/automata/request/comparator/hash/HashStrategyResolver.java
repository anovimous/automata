package com.automata.request.comparator.hash;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.automata.request.comparator.hash.strategy.HashStrategy;

@Component
public class HashStrategyResolver {

	private final Map<HashTarget, HashStrategy> strategies;

	public HashStrategyResolver(List<HashStrategy> strategies) {
		this.strategies = strategies.stream().collect(Collectors.toMap(HashStrategy::supports, s -> s));
	}

	public HashStrategy resolve(HashTarget target) {

		HashStrategy strategy = strategies.get(target);

		if (strategy == null)
			throw new IllegalArgumentException("No corresponding strategy found for supplied enum");

		return strategy;
	}

}
