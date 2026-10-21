package com.automata.job.domain.exception;

import java.util.Collection;

public class InvalidCustomConfigException extends IllegalArgumentException {

	public InvalidCustomConfigException(String routineKey, Collection<String> violations) {
		super("customConfig is not valid for routine '" + routineKey + "': " + String.join("; ", violations));
	}

}
