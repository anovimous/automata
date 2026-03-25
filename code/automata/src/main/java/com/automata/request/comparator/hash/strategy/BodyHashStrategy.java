package com.automata.request.comparator.hash.strategy;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.automata.request.Request;
import com.automata.request.body.BodyProperty;
import com.automata.request.comparator.hash.HashTarget;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;

@Component
public class BodyHashStrategy implements HashStrategy {

	@Override
	public HashTarget supports() {

		return HashTarget.BODY;

	}

	@Override
	public HashCode hashStrategyTarget(Request request, HashFunction function) {

		String normalizedInput = this.normalize(request.getBodyProperties());

		HashCode hash = function.hashString(normalizedInput, Charset.defaultCharset());

		return hash;

	}

	private String normalize(List<BodyProperty> properties) {

		Map<String, List<BodyProperty>> propertiesGroupedOnParentId = properties.stream().collect(Collectors.groupingBy(
				(property) -> this.computeLastObjectParent(property.getFullPath(), property.getIsArrayElement())));

		List<List<BodyProperty>> groupedPropertiesInnerSorted = propertiesGroupedOnParentId.values().stream()
				.map((list) -> list.stream().sorted((prop1, prop2) -> this
						.computeLastObjectParent(prop1.getFullPath(), prop1.getIsArrayElement()).compareToIgnoreCase(
								this.computeLastObjectParent(prop2.getFullPath(), prop2.getIsArrayElement())))
						.toList())
				.toList();

		List<String> stringsOfEachGroupLowercase = groupedPropertiesInnerSorted.stream().map((list) -> {

			StringBuilder builder = new StringBuilder();

			list.forEach((bodyProperty) -> {
				String fullPath = bodyProperty.getFullPath();
				if (bodyProperty.getIsArrayElement())
					builder.append(fullPath.substring(0, fullPath.lastIndexOf("[")).toLowerCase());
				else
					builder.append(fullPath.toLowerCase());
			});

			return builder.toString();

		}).sorted(String::compareTo).toList();

		;
		StringBuilder builder = new StringBuilder();

		stringsOfEachGroupLowercase.forEach((str) -> builder.append(str));

		return builder.toString();

	}

	private String computeLastObjectParent(String fullPath, Boolean isArrayElement) {

		if (!fullPath.contains("."))
			return "";

		if (isArrayElement) {

			int firstBracketIndex = fullPath.indexOf("[");

			int lastPointBeforeBracket = fullPath.substring(0, firstBracketIndex).lastIndexOf(".");
			return fullPath.substring(0, lastPointBeforeBracket);

		} else {
			int lastPointIndex = fullPath.lastIndexOf(".");
			return fullPath.substring(0, lastPointIndex);
		}
	}

}
