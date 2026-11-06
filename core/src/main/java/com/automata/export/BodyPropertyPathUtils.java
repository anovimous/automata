package com.automata.export;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the names inside the {@code full_path} of a body property. The path is
 * either what json-flattener writes for a JSON body ({@code a.b[0].c}; a key
 * holding a {@code .} or a {@code [} is written {@code ["a.b"]}, and quotes
 * inside it are not escaped) or the name of a form field as it was sent
 * ({@code filter[name]}).
 */
public abstract class BodyPropertyPathUtils {

	/**
	 * The names of a path, in order: each plain segment, the key of a quoted
	 * segment and the inner name of an unquoted bracket ({@code [name]}). An array
	 * index ({@code [0]}) is not a name. A form field with a bracket that is not an
	 * index ({@code filter[name]}, {@code tags[]}) also exports its whole name last,
	 * since that is the parameter the server reads. A malformed path never throws:
	 * what cannot be read is taken as it is.
	 */
	public static List<String> extractNames(String fullPath) {

		Set<String> names = new LinkedHashSet<>();
		boolean formField = false;
		StringBuilder current = new StringBuilder();
		int i = 0;
		int length = fullPath.length();

		while (i < length) {

			char c = fullPath.charAt(i);

			if (c == '.') {
				flush(current, names);
				i++;
			} else if (c == '[') {
				flush(current, names);

				if (fullPath.startsWith("[\"", i)) {
					int close = findQuotedEnd(fullPath, i + 2);
					if (close < 0) {
						add(names, fullPath.substring(i + 2));
						break;
					}
					add(names, fullPath.substring(i + 2, close));
					i = close + 2;
				} else {
					int close = fullPath.indexOf(']', i + 1);
					if (close < 0) {
						add(names, fullPath.substring(i + 1));
						break;
					}
					String inner = fullPath.substring(i + 1, close);
					if (!isIndex(inner)) {
						add(names, inner);
						formField = true;
					}
					i = close + 1;
				}
			} else {
				current.append(c);
				i++;
			}
		}

		flush(current, names);

		if (formField)
			names.add(fullPath);

		return new ArrayList<>(names);

	}

	// the first `"]` which ends the path or is followed by the next segment
	private static int findQuotedEnd(String path, int from) {

		int close = path.indexOf("\"]", from);

		while (close >= 0) {
			int next = close + 2;
			if (next == path.length() || path.charAt(next) == '.' || path.charAt(next) == '[')
				return close;
			close = path.indexOf("\"]", close + 1);
		}

		return -1;

	}

	private static boolean isIndex(String inner) {
		return !inner.isEmpty() && inner.chars().allMatch(Character::isDigit);
	}

	private static void flush(StringBuilder current, Set<String> names) {
		add(names, current.toString());
		current.setLength(0);
	}

	private static void add(Set<String> names, String name) {
		if (!name.isBlank())
			names.add(name);
	}

}
