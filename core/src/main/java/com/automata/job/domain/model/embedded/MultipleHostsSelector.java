package com.automata.job.domain.model.embedded;

import java.util.Set;

import lombok.Data;

@Data
public class MultipleHostsSelector {

	private Set<Long> hostIds;

}
