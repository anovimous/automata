package com.automata.job.domain.model.enums;

// What kind of failure a worker reports with a FAILED or SELFTERMINATED job. It tells a reader what to do
// next, the message tells what happened. A worker that reports nothing, or a value the core does not know, gives UNKNOWN.
public enum FailureCategory {

	// the worker could not set the job up: work directory, files download, routine mapping
	PREPARATION,
	// the first poll of the core failed
	POLL,
	// the job details could not be parsed or turned into a command (unknown flag, no wordlist, bad customConfig)
	INVALID_CONFIG,
	// the tool could not be started
	TOOL_START,
	// the tool ran and exited with an error
	TOOL_EXIT,
	// the result is missing or could not be uploaded
	RESULT,
	UNKNOWN;

	// The category a worker reported as text: a missing or unknown name is UNKNOWN
	public static FailureCategory fromReported(String reported) {
		if (reported == null)
			return UNKNOWN;
		for (FailureCategory category : values())
			if (category.name().equals(reported.trim()))
				return category;
		return UNKNOWN;
	}

}
