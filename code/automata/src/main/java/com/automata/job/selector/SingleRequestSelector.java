package com.automata.job.selector;

import lombok.Data;

@Data
public class SingleRequestSelector {

	private Long requestId;

	// CANCELED: private String requestBase64;
	// Must be provided in custom config

}
