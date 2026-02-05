package com.automata.request.body;

import com.automata.request.common.enums.PropertyValueType;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Builder;

import lombok.Getter;
import lombok.Setter;

@Builder
@AllArgsConstructor
@Getter
@Setter
public class TemporaryTraversalProperty {

	private int id;
	private String property;
	private String value;
	private PropertyValueType type;
	private Integer parentId;
	private JsonNode node;

}
