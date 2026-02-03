package com.automata.request.body;

import java.util.ArrayList;
import java.util.List;

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
		
		//COMPLETE HERE: return BodyParseResult

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
		// TODO Auto-generated method stub
		return null;
	}

}
