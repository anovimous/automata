package com.automata.job.domain.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GenericConfig {

	public static GenericConfig of(MatchAndReplace matchAndReplace) {
		return new GenericConfig(matchAndReplace);
	}

	private MatchAndReplace matchAndReplace;

}
