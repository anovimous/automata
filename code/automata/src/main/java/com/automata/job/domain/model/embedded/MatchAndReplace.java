package com.automata.job.domain.model.embedded;

import com.automata.job.domain.model.enums.MatchAndReplaceRule;
import com.automata.job.domain.model.enums.MatchAndReplaceTargetType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatchAndReplace {

	MatchAndReplaceTargetType targetType;

	MatchAndReplaceRule rule;

	String targetKey;

}
