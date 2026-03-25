package com.automata.job.selector.matcher;

import java.util.List;

import com.automata.request.Request;
import com.automata.request.parameter.QueryParameter;

public abstract class RequestPredicateTargetComponentGetter {

	public static String get(PredicateTargetComponent component, Request request) {

		return switch (component) {
		case PredicateTargetComponent.PATH -> request.getComputatedPath();
		case PredicateTargetComponent.METHOD -> request.getMethod().toString();
		case PredicateTargetComponent.EXTENSION -> request.getExtension();
		case PredicateTargetComponent.CONTENT_TYPE -> request.getContentType().getRaw();
		case PredicateTargetComponent.QUERYSTRING -> RequestPredicateTargetComponentGetter.getQueryString(request);
		default -> throw new IllegalArgumentException("This component is not supported yet");
		};

	}

	private static String getQueryString(Request request) {

		StringBuilder builder = new StringBuilder();

		List<QueryParameter> parameters = request.getParameters();

		int size = parameters.size();

		if (size != 0)
			builder.append("?");
		else
			return "";

		for (int i = 0; i < size; i++) {
			QueryParameter param = parameters.get(i);
			builder.append(String.format("%1$=%2$", param.getParameter(), param.getValue()));
			if (i != size - 1)
				builder.append("&");
		}

		return builder.toString();

	}

}
