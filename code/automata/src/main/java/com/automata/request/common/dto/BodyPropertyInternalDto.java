package com.automata.request.common.dto;

import com.automata.request.common.enums.PropertyValueType;

public record BodyPropertyInternalDto(String fullPath, String value, PropertyValueType type, Long requestId,
		Long responseId) {

}
