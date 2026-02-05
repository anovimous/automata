package com.automata.request.body;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import com.automata.request.common.enums.PropertyValueType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;

public abstract class BodyUtils {

	public static BodyParseResult parseJsonBody(String body) {

		ObjectMapper mapper = new ObjectMapper();

		JsonNode root;
		try {
			root = mapper.readTree(body);
		} catch (Exception e) {
			throw new IllegalArgumentException("Json body malformed");
		}

		List<TemporaryTraversalProperty> temporaryProperties = new ArrayList<>();

		PropertyValueType type = root.isObject() ? PropertyValueType.OBJECT : PropertyValueType.ARRAY;

		TemporaryTraversalProperty rootProperty = TemporaryTraversalProperty.builder().id(0).node(root).property(null)
				.value(null).type(type).parentId(null).build();

		temporaryProperties.add(rootProperty);

		flattenJsonIntoBodyProperties(rootProperty, temporaryProperties);

		List<BodyProperty> bodyProperties = temporaryProperties.stream()
				.map((tempProp) -> mapper.convertValue(tempProp, BodyProperty.class)).collect(Collectors.toList());

		BodyParseResult result = new BodyParseResult();

		result.setBodyProperties(bodyProperties);

		return result;

	}

	private static void flattenJsonIntoBodyProperties(TemporaryTraversalProperty addedProperty,
			List<TemporaryTraversalProperty> list) {

		if (addedProperty.getType() == PropertyValueType.OBJECT)
			addedProperty.getNode().properties().forEach((entry) -> {

				PropertyValueType type = detectPropertyValueTypeFromJsonNode(entry.getValue());

				TemporaryTraversalProperty newProperty = TemporaryTraversalProperty.builder().id(list.size())
						.property(entry.getKey()).value(null).type(type).parentId(addedProperty.getId()).build();

				list.add(newProperty);

				flattenJsonIntoBodyProperties(newProperty, list);

			});
		else if (addedProperty.getType() == PropertyValueType.OBJECT)
			for (JsonNode element : addedProperty.getNode()) {

				PropertyValueType type = detectPropertyValueTypeFromJsonNode(element);

				TemporaryTraversalProperty newProperty = TemporaryTraversalProperty.builder().id(list.size())
						.property(null).value(null).type(type).parentId(addedProperty.getId()).build();

				list.add(newProperty);

				flattenJsonIntoBodyProperties(newProperty, list);

			}
		else {
			addedProperty.setValue(addedProperty.getNode().asText());
		}

	}

	private static PropertyValueType detectPropertyValueTypeFromJsonNode(JsonNode node) {

		return switch (node.getNodeType()) {

		case JsonNodeType.ARRAY -> PropertyValueType.ARRAY;
		case JsonNodeType.NUMBER -> node.isFloatingPointNumber() ? PropertyValueType.INT : PropertyValueType.FLOAT;
		case JsonNodeType.OBJECT -> PropertyValueType.OBJECT;
		case JsonNodeType.STRING -> PropertyValueType.STRING;
		case JsonNodeType.BOOLEAN -> PropertyValueType.BOOLEAN;
		case JsonNodeType.NULL -> PropertyValueType.NULL;

		default -> throw new IllegalArgumentException();
		};
	}

	public static BodyParseResult parseFormBody(String body) {

		URIBuilder builder = new URIBuilder().setCustomQuery(body);

		List<NameValuePair> pairs = builder.getQueryParams();

		List<BodyProperty> bodyProperties = pairs.stream()
				.map((pair) -> BodyProperty.of(pair.getName(), pair.getValue(),
						detectPropertyValueTypeFromFormParameterValue(pair.getValue()), null))
				.collect(Collectors.toList());

		BodyParseResult result = new BodyParseResult();

		result.setBodyProperties(bodyProperties);

		return result;

	}

	private static PropertyValueType detectPropertyValueTypeFromFormParameterValue(String value) {

		// Note that in FORM body the only options are STRING, INT, DOUBLE, BOOLEAN

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

		if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))
			return PropertyValueType.BOOLEAN;

		return PropertyValueType.STRING;

	}

}
