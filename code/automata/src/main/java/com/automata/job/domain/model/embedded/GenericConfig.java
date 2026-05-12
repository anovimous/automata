package com.automata.job.domain.model.embedded;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenericConfig {

	public static GenericConfig of(List<MatchAndReplace> matchAndReplace) {
		return new GenericConfig(matchAndReplace);
	}

	private List<MatchAndReplace> matchAndReplace;

}
