package com.automata.request.parameter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.WWWFormCodec;

import com.automata.request.common.dto.QueryParameterInternalDto;

public abstract class QueryParameterUtils {

	public static QueryStringParseResult parseQueryString(String queryString) {

		if (queryString.startsWith("?"))
			queryString = queryString.substring(1);

		List<NameValuePair> pairs = WWWFormCodec.parse(queryString, StandardCharsets.UTF_8);

		List<QueryParameter> queryParameters = pairs.stream()
				.map((pair) -> QueryParameter.of(pair.getName(), pair.getValue())).collect(Collectors.toList());

		QueryStringParseResult parseResult = new QueryStringParseResult();

		parseResult.setQueryParameters(queryParameters);

		return parseResult;
	}

	public static Optional<String> composeRawQueryString(List<QueryParameterInternalDto> queryParameterDtos) {

		if (!queryParameterDtos.isEmpty()) {
			List<BasicNameValuePair> apacheNameValuePairs = queryParameterDtos.stream()
					.map((dto) -> new BasicNameValuePair(dto.parameter(), dto.value())).toList();

			String queryString = WWWFormCodec.format(apacheNameValuePairs, StandardCharsets.UTF_8);

			return Optional.of(queryString);
		} else
			return Optional.empty();
	}

}
