package com.automata.request.comparator.hash.strategy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.automata.request.Request;
import com.automata.request.body.BodyProperty;
import com.automata.request.comparator.hash.HashTarget;
import com.automata.request.common.enums.RequestContentType;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;

@Component
public class BodyHashStrategy implements HashStrategy {

	private static final String ROOT_ARRAY = "[]";

	// Stands in for the body paths when there are none. Starts with a control
	// character, so it can never be equal to a path.
	private static final String NO_PROPERTIES_MARKER = "\u0000content-type:";

	@Override
	public HashTarget supports() {

		return HashTarget.BODY;

	}

	@Override
	public HashCode hashStrategyTarget(Request request, HashFunction function) {

		String normalizedInput = this.normalize(request.getBodyProperties(), request.getContentType());

		return function.hashString(normalizedInput, StandardCharsets.UTF_8);

	}

	// Only the structure of the body matters: values, value types, key order and
	// everything inside an array (length, order, element contents) are ignored. An
	// array is represented by its name alone. A body without properties (multipart,
	// XML, text, ... or no body at all) is represented by its content type.
	private String normalize(List<BodyProperty> properties, RequestContentType contentType) {

		if (properties.isEmpty())
			return NO_PROPERTIES_MARKER + (contentType == null ? "none" : contentType.name());

		Set<String> paths = new TreeSet<>();

		for (BodyProperty property : properties) {

			String path = property.getFullPath().toLowerCase();

			if (contentType == RequestContentType.JSON)
				path = this.cutAtArray(path);

			paths.add(path);

		}

		return String.join("\n", paths);

	}

	private String cutAtArray(String path) {

		int bracketIndex = path.indexOf("[");

		if (bracketIndex < 0)
			return path;

		return bracketIndex == 0 ? ROOT_ARRAY : path.substring(0, bracketIndex);

	}

}
