package com.automata.job.domain.model.embedded;

import lombok.Data;

@Data
public class SingleRequestSelector {

	private Long requestId;

	// CANCELED: private String requestBase64;
	// Must be provided in custom config

}
