package com.automata.request.comparator.hash.strategy;

import java.nio.charset.Charset;

import org.springframework.stereotype.Component;

import com.automata.request.Request;
import com.automata.request.comparator.hash.HashTarget;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;

@Component
public class MethodHashStrategy implements HashStrategy {

	@Override
	public HashTarget supports() {

		return HashTarget.METHOD;
	}

	@Override
	public HashCode hashStrategyTarget(Request request, HashFunction function) {

		String method = request.getMethod().toString();

		HashCode hash = function.hashString(method, Charset.defaultCharset());
		
		return hash;

	}

}
