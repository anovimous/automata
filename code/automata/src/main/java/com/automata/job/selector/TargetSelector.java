package com.automata.job.selector;

import com.automata.job.common.enums.SelectorType;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TargetSelector {

	private SelectorType selectorType;

	private JsonNode selector;

}
