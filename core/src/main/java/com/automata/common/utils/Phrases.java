package com.automata.common.utils;

import java.util.List;

/**
 * Small helpers for messages which count things: "2 jobs and 1 finding".
 */
public abstract class Phrases {

	/** "1 finding", "2 findings", "0 findings" */
	public static String count(long number, String singular, String plural) {
		return number + " " + (number == 1 ? singular : plural);
	}

	/** "a", "a and b", "a, b and c"; empty for no parts */
	public static String list(List<String> parts) {

		if (parts.isEmpty())
			return "";

		if (parts.size() == 1)
			return parts.get(0);

		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);

	}

}
