package com.automata.job.domain.model.embedded;

import com.automata.job.domain.model.enums.SelectorType;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TargetSelector {

	private SelectorType selectorType;

	private JsonNode selector;

}
