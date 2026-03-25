package com.automata.request.body;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.net.WWWFormCodec;

import com.automata.request.body.BodyProperty.BodyPropertyBuilder;
import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.enums.ContentType;
import com.automata.request.common.enums.PropertyValueType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wnameless.json.flattener.JsonFlattener;
import com.github.wnameless.json.unflattener.JsonUnflattener;

public abstract class BodyUtils {

	public static BodyParseResult parseJsonBody(String body) {

		ObjectMapper mapper = new ObjectMapper();

		JsonNode root;
		try {
			root = mapper.readTree(body);
		} catch (Exception e) {
			throw new IllegalArgumentException("Json body malformed");
		}

		Map<String, Object> flattenedJsonMap = JsonFlattener.flattenAsMap(root.toString());

		List<BodyProperty> properties = new ArrayList<>();

		for (Map.Entry<String, Object> entry : flattenedJsonMap.entrySet()) {

			String value = Objects.toString(entry.getValue(), null);

			BodyPropertyBuilder builder = BodyProperty.builder().fullPath(entry.getKey()).value(value);

			builder.isArrayElement(entry.getKey().endsWith("]"));

			builder.propertyValueType(BodyUtils.detectPropertyValueTypeFromValue(value));

			properties.add(builder.build());

		}

		return new BodyParseResult(properties);

	}

	public static BodyParseResult parseFormBody(String body) {

		URIBuilder builder = new URIBuilder().setCustomQuery(body);

		List<NameValuePair> pairs = builder.getQueryParams();

		List<BodyProperty> bodyProperties = pairs.stream()
				.map((pair) -> BodyProperty.builder().fullPath(pair.getName()).value(pair.getValue())
						.propertyValueType(BodyUtils.detectPropertyValueTypeFromValue(pair.getValue())).build())
				.collect(Collectors.toList());

		BodyParseResult result = new BodyParseResult();

		result.setBodyProperties(bodyProperties);

		return result;

	}

	private static PropertyValueType detectPropertyValueTypeFromValue(String value) {

		// Note that in FORM body the only options are STRING, INT, DOUBLE, BOOLEAN

		if (value == null) {
			return PropertyValueType.NULL;
		}

		if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))
			return PropertyValueType.BOOLEAN;

		try {
			Integer.parseInt(value);
			return PropertyValueType.INT;
		} catch (Exception e) {
		}

		try {
			Float.parseFloat(value);
			return PropertyValueType.FLOAT;
		} catch (Exception e) {
		}

		return PropertyValueType.STRING;

	}

	public static Optional<String> composeRawBody(ContentType contentType,
			List<BodyPropertyInternalDto> bodyPropertyDtos) {

		if (contentType.equals(ContentType.JSON))
			return Optional.of(composeRawJsonBody(bodyPropertyDtos));
		else
			return Optional.of(composeRawFormBody(bodyPropertyDtos));

	}

	private static String composeRawJsonBody(List<BodyPropertyInternalDto> bodyPropertyDtos) {

		Map<String, Object> flattenedMap = bodyPropertyDtos.stream()
				.collect(Collectors.toMap(dto -> dto.fullPath(), dto -> dto.value()));

		return JsonUnflattener.unflatten(flattenedMap);

	}

	private static String composeRawFormBody(List<BodyPropertyInternalDto> bodyPropertyDtos) {

		List<BasicNameValuePair> apachePairs = bodyPropertyDtos.stream()
				.map(dto -> new BasicNameValuePair(dto.fullPath(), dto.value())).toList();

		return WWWFormCodec.format(apachePairs, StandardCharsets.UTF_8);

	}

}
