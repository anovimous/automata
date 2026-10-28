package com.automata.job.domain.model.embedded;

import com.automata.job.domain.model.enums.SelectorType;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TargetSelector {

	@NotNull
	private SelectorType selectorType;

	@NotNull
	private JsonNode selector;

}
