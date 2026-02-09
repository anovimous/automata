package com.automata.request.comparator.hash.strategy;

import java.nio.charset.Charset;

import org.springframework.stereotype.Component;

import com.automata.request.Request;
import com.automata.request.comparator.hash.HashTarget;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;

@Component
public class PathHashStrategy implements HashStrategy {

	@Override
	public HashTarget supports() {

		return HashTarget.PATH;
	}

	@Override
	public HashCode hashStrategyTarget(Request request, HashFunction function) {

		String computatedPath = request.getComputatedPath();

		HashCode hash = function.hashString(computatedPath, Charset.defaultCharset());

		return hash;

	}

}
