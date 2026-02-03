package com.automata.request.parameter;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;

public abstract class QueryParameterUtils {

	public static QueryStringParseResult parseQueryString(String queryString) {

		if (queryString.startsWith("?"))
			queryString = queryString.substring(1);

		URIBuilder builder = new URIBuilder().setCustomQuery(queryString);

		List<NameValuePair> pairs = builder.getQueryParams();

		List<QueryParameter> queryParameters = pairs.stream()
				.map((pair) -> QueryParameter.of(pair.getName(), pair.getValue())).collect(Collectors.toList());

		QueryStringParseResult parseResult = new QueryStringParseResult();

		parseResult.setQueryParameters(queryParameters);

		return parseResult;
	}

}
