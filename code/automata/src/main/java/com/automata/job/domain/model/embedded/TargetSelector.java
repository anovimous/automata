package com.automata.job.domain.model.embedded;

import com.automata.job.domain.model.enums.SelectorType;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TargetSelector {

	private SelectorType selectorType;

	private JsonNode selector;

}
