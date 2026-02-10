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

		Map<Long, List<BodyProperty>> propertiesGroupedOnParentId = properties.stream()
				.collect(Collectors.groupingBy(BodyProperty::getParentId));

		List<List<BodyProperty>> groupedPropertiesInnerSorted = propertiesGroupedOnParentId.values().stream()
				.map((list) -> list.stream()
						.sorted((prop1, prop2) -> prop1.getProperty().compareToIgnoreCase(prop1.getProperty()))
						.toList())
				.toList();

		List<String> stringsOfEachGroupLowercase = groupedPropertiesInnerSorted.stream()
				.filter((list) -> list.getFirst().getProperty() != null).map((list) -> {

					StringBuilder builder = new StringBuilder();

					list.forEach((bodyProperty) -> builder.append(bodyProperty.getProperty().toLowerCase()));

					return builder.toString();

				}).sorted(String::compareTo).toList();

		;
		StringBuilder builder = new StringBuilder();

		stringsOfEachGroupLowercase.forEach((str) -> builder.append(str));

		return builder.toString();

	}

}
