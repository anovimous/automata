package com.automata.request.path;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.automata.common.utils.UUIDUtils;
import com.automata.common.utils.ValidationResult;
import com.automata.request.common.dto.PathVariableInternalDto;
import com.automata.request.common.enums.PathVariableValueType;

public abstract class PathUtils {

	/**
	 * @param path the path as it was sent, still percent-encoded: decoding it
	 *             would make "%20", "%25" or "%3F" ambiguous or illegal
	 */
	public static PathParsingResult parsePath(String path) {

		// a query string or a fragment which came with it is not part of the path
		int pathEnd = path.length();
		for (char separator : new char[] { '?', '#' }) {
			int index = path.indexOf(separator);
			if (index >= 0 && index < pathEnd)
				pathEnd = index;
		}

		String cleanedPath = path.substring(0, pathEnd);

		PathParsingResult parseResult = new PathParsingResult();

		parseResult.setExtension(extensionOf(cleanedPath));

		// -1 keeps the empty segments at the end: "/" is the root and "/a/" is not "/a"
		List<String> uriSplits = Arrays.asList(cleanedPath.split("/", -1));

		List<String> newUriSplits = new ArrayList<>();

		for (int i = 0; i < uriSplits.size(); i++) {

			String part = uriSplits.get(i);

			if (UUIDUtils.isValidUUID(part)) {

				newUriSplits.add(String.join("@", Integer.toString(i), PathVariableValueType.GUID.toString()));

				parseResult.getPathVariables().add(PathVariable.of(i, PathVariableValueType.GUID, part));

			} else if (part.matches("\\d+")) {

				newUriSplits.add(String.join("@", Integer.toString(i), PathVariableValueType.INT.toString()));

				parseResult.getPathVariables().add(PathVariable.of(i, PathVariableValueType.INT, part));

			} else {
				newUriSplits.add(part);
			}

		}

		parseResult.setComputatedPath(String.join("/", newUriSplits));
		return parseResult;

	}

	/**
	 * The full extension of the last segment, in lower case: everything from its first
	 * dot, so ".min.js" and ".tar.gz" and not only the last part. There is none when the
	 * dot belongs to an earlier segment ("/v1.2/users"), when the segment only starts
	 * with a dot (a hidden file such as ".env", whose extension would be the file
	 * itself) and when nothing but dots follows the name ("a.", "..").
	 */
	static String extensionOf(String path) {

		String lastSegment = path.substring(path.lastIndexOf('/') + 1);

		// from the second character on: a leading dot is part of the name
		int firstDot = lastSegment.indexOf('.', 1);

		if (firstDot < 0)
			return null;

		String extension = lastSegment.substring(firstDot);

		if (extension.chars().allMatch(c -> c == '.'))
			return null;

		return extension.toLowerCase(Locale.ROOT);

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

		// .split here will result in an entry of "" at the index 0, and at the end for a trailing slash
		List<String> computatedUriSplits = Arrays.asList(computatedPath.split("/", -1));

		Map<Integer, PathVariableInternalDto> dtosMap = pathVariableDtos.stream()
				.collect(Collectors.toMap(PathVariableInternalDto::index, dto -> dto));
		
		List<String> originalUriSplits = computatedUriSplits.stream().map((part) -> {

			if (part.matches("[0-9]+@(INT|GUID)")) {
				Integer pathIndex = Integer.parseInt(part.substring(0, part.indexOf('@')));
				return dtosMap.get(pathIndex).value();
			} else
				return part;

		}).toList();

		String originalPath = String.join("/", originalUriSplits);

		return originalPath;

	}

}
