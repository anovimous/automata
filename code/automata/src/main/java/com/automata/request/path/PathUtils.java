package com.automata.request.path;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import com.automata.common.utils.UUIDUtils;
import com.automata.common.utils.ValidationResult;
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

}
