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

		// "?debug" has no value: the column is NOT NULL, so it is stored as "?debug=" (an empty value)
		List<NameValuePair> pairs = WWWFormCodec.parse(percentEncodeNonAscii(queryString), StandardCharsets.UTF_8);

		List<QueryParameter> queryParameters = pairs.stream()
				.map((pair) -> QueryParameter.of(pair.getName(), pair.getValue() == null ? "" : pair.getValue()))
				.collect(Collectors.toList());

		QueryStringParseResult parseResult = new QueryStringParseResult();

		parseResult.setQueryParameters(queryParameters);

		return parseResult;
	}

	/**
	 * The query as the codec reads it: a character which is not ASCII (the browsers
	 * and the tools send them as they are) becomes the percent-encoded bytes of its
	 * UTF-8 form, since the codec would take its low byte alone.
	 */
	static String percentEncodeNonAscii(String queryString) {

		if (queryString.chars().allMatch(c -> c < 128))
			return queryString;

		StringBuilder encoded = new StringBuilder();

		queryString.codePoints().forEach(codePoint -> {
			if (codePoint < 128)
				encoded.appendCodePoint(codePoint);
			else
				for (byte b : new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8))
					encoded.append('%').append(String.format("%02X", b & 0xFF));
		});

		return encoded.toString();

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
