package com.automata.request.path;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.automata.common.utils.UUIDUtils;
import com.automata.common.utils.ValidationResult;
import com.automata.request.common.dto.PathVariableInternalDto;
import com.automata.request.common.enums.PathVariableValueType;

public abstract class PathUtils {

	public static PathParsingResult parsePath(String path) {

		String cleanedPath = URI.create(path).getPath();

		int lastSlashIndex = cleanedPath.lastIndexOf("/");

		int lastDotIndex = cleanedPath.lastIndexOf(".", lastSlashIndex);

		PathParsingResult parseResult = new PathParsingResult();

		String extension = cleanedPath.substring(lastDotIndex);

		parseResult.setExtension(extension);

		List<String> uriSplits = Arrays.asList(cleanedPath.split("/"));

		for (int i = 0; i < uriSplits.size(); i++) {

			String part = uriSplits.get(i);

			if (UUIDUtils.isValidUUID(part)) {

				uriSplits.set(i, String.join("@", Integer.toString(i), PathVariableValueType.GUID.toString()));

				parseResult.getPathVariables().add(PathVariable.of(i, PathVariableValueType.GUID, part));

			} else if (part.matches("\\d+")) {

				uriSplits.set(i, String.join("@", Integer.toString(i), PathVariableValueType.INT.toString()));

				parseResult.getPathVariables().add(PathVariable.of(i, PathVariableValueType.INT, part));

			}

		}

		parseResult.setComputatedPath(String.join("", uriSplits));
		return parseResult;

	}

	public static ValidationResult validatePath(String rawPath) {

		URI uri;

		try {
			uri = new URI(rawPath);
		} catch (URISyntaxException e) {
			return ValidationResult.invalid();
		}

		String path = uri.getPath();

		if (path == null || !path.startsWith("/")) {
			return ValidationResult.invalid();
		}

		return ValidationResult.valid();

	}

	public static String composeRawPath(String computatedPath, List<PathVariableInternalDto> pathVariableDtos) {

		List<String> computatedUriSplits = Arrays.asList(computatedPath.split("/"));

		Map<Integer, PathVariableInternalDto> dtosMap = pathVariableDtos.stream()
				.collect(Collectors.toMap(PathVariableInternalDto::index, dto -> dto));

		List<String> originalUriSplits = computatedUriSplits.stream().map((part) -> {

			if (part.matches("[0-9]+@(INT|GUID)")) {
				int pathIndex = Integer.parseInt(part.substring(0, part.indexOf('@')));
				return dtosMap.get(pathIndex).value();
			} else
				return part;

		}).toList();

		String originalPath = String.join("/", originalUriSplits);

		return originalPath;

	}

}
