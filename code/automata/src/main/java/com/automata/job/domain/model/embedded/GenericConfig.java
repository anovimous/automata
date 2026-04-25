package com.automata.job.domain.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenericConfig {

	public static GenericConfig of(MatchAndReplace matchAndReplace) {
		return new GenericConfig(matchAndReplace);
	}

	private MatchAndReplace matchAndReplace;

}
