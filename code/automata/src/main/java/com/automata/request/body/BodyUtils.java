package com.automata.request.body;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.WWWFormCodec;

import com.automata.request.body.BodyProperty.BodyPropertyBuilder;
import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.enums.PropertyValueType;
import com.automata.request.common.enums.RequestContentType;
import com.automata.response.ResponseContentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wnameless.json.flattener.JsonFlattener;
import com.github.wnameless.json.unflattener.JsonUnflattener;

public abstract class BodyUtils {

	// RAW -> OBJECT

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

			PropertyValueType type = BodyUtils.detectPropertyValueTypeFromValue(entry.getValue());

			String value = getStringValue(entry.getValue(), type);

			BodyPropertyBuilder builder = BodyProperty.builder().fullPath(entry.getKey()).value(value);

			builder.isArrayElement(entry.getKey().endsWith("]"));

			builder.propertyValueType(type);

			properties.add(builder.build());

		}

		return new BodyParseResult(properties);

	}

	public static BodyParseResult parseFormBody(String body) {

		List<NameValuePair> pairs = WWWFormCodec.parse(body, StandardCharsets.UTF_8);

		List<BodyProperty> bodyProperties = pairs.stream().map((pair) -> BodyProperty.builder().fullPath(pair.getName())
				.value(pair.getValue()).propertyValueType(PropertyValueType.STRING).build())
				.collect(Collectors.toList());

		BodyParseResult result = new BodyParseResult();

		result.setBodyProperties(bodyProperties);

		return result;

	}

	// OBJECT -> RAW

	public static Optional<String> composeRequestRawBody(RequestContentType contentType,
			List<BodyPropertyInternalDto> bodyPropertyDtos) {

		if (contentType.equals(RequestContentType.JSON))
			return Optional.ofNullable(composeRawJsonBody(bodyPropertyDtos));
		else
			return Optional.ofNullable(composeRawFormBody(bodyPropertyDtos));

	}

	public static Optional<String> composeResponseRawBody(ResponseContentType contentType,
			List<BodyPropertyInternalDto> bodyPropertyDtos) {

		return Optional.ofNullable(composeRawJsonBody(bodyPropertyDtos));

	}

	private static String composeRawJsonBody(List<BodyPropertyInternalDto> bodyPropertyDtos) {

		Map<String, Object> flattenedMap = new LinkedHashMap<>();
		
		bodyPropertyDtos.forEach(dto -> 
		    flattenedMap.put(dto.fullPath(), getOriginalObject(dto.value(), dto.type()))
		);

		return JsonUnflattener.unflatten(flattenedMap);

	}

	private static String composeRawFormBody(List<BodyPropertyInternalDto> bodyPropertyDtos) {

		List<BasicNameValuePair> apachePairs = bodyPropertyDtos.stream()
				.map(dto -> new BasicNameValuePair(dto.fullPath(), dto.value())).toList();

		return WWWFormCodec.format(apachePairs, StandardCharsets.UTF_8);

	}

	// Helpers for Type handling

	private static PropertyValueType detectPropertyValueTypeFromValue(Object value) {

		// Note that in FORM body the only options are STRING

		return switch (value) {

		case null -> PropertyValueType.NULL;
		case String s -> PropertyValueType.STRING;
		case Integer i -> PropertyValueType.INT;
		case Long l -> PropertyValueType.LONG;
		case Double d -> PropertyValueType.DOUBLE;
		case Boolean b -> PropertyValueType.BOOLEAN;
		case Map<?, ?> m -> PropertyValueType.EMPTY_OBJECT;
		case List<?> l -> PropertyValueType.EMPTY_ARRAY;
		default -> throw new RuntimeException("Couldn't detect value type of property");
		};

	}

	private static Object getOriginalObject(String value, PropertyValueType type) {

		if (value == null) {
			return switch (type) {
			case EMPTY_OBJECT -> new LinkedHashMap<>();
			case EMPTY_ARRAY -> new ArrayList<>();
			case NULL -> null;
			default -> throw new IllegalArgumentException("Unexpected value(null) for the type" + type);
			};
		}

		return switch (type) {
		case INT -> Integer.valueOf(value);
		case LONG -> Long.valueOf(value);
		case DOUBLE -> Double.valueOf(value);
		case BOOLEAN -> Boolean.valueOf(value);
		default -> value;
		};

	}

	private static String getStringValue(Object value, PropertyValueType type) {

		return switch (type) {
		case NULL -> null;
		case EMPTY_OBJECT -> null;
		case EMPTY_ARRAY -> null;
		default -> value.toString();
		};

	}

}
