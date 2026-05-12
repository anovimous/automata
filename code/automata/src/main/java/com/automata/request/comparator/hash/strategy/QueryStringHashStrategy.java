package com.automata.request.comparator.hash.strategy;

import java.nio.charset.Charset;
import java.util.List;

import org.springframework.stereotype.Component;

import com.automata.request.Request;
import com.automata.request.comparator.hash.HashTarget;
import com.automata.request.parameter.QueryParameter;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;

@Component
public class QueryStringHashStrategy implements HashStrategy {

	@Override
	public HashTarget supports() {

		return HashTarget.QUERYSTRING;

	}

	@Override
	public HashCode hashStrategyTarget(Request request, HashFunction function) {

		String normalizedInput = this.normalize(request.getParameters());

		HashCode hash = function.hashString(normalizedInput, Charset.defaultCharset());

		return hash;

	}

	private String normalize(List<QueryParameter> parameters) {

		parameters = parameters.stream().sorted((a, b) -> a.getParameter().compareTo(b.getParameter())).toList();

		StringBuilder builder = new StringBuilder();

		for (QueryParameter param : parameters)
			builder.append(param.getParameter());

		return builder.toString();

	}

}
