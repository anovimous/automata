package com.automata.request.parameter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.net.URLEncodedUtils;

import com.automata.request.common.dto.QueryParameterInternalDto;

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

	@SuppressWarnings("deprecation")
	public static Optional<String> composeRawQueryString(List<QueryParameterInternalDto> queryParameterDtos) {

		List<BasicNameValuePair> apacheNameValuePairs = queryParameterDtos.stream()
				.map((dto) -> new BasicNameValuePair(dto.parameter(), dto.value())).toList();

		String queryString = URLEncodedUtils.format(apacheNameValuePairs, StandardCharsets.UTF_8);

		return Optional.of(queryString);
	}

}
