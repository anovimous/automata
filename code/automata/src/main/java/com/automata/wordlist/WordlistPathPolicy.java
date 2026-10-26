package com.automata.wordlist;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// A wordlist path is read by the worker as a filesystem path, so only paths inside the configured
// base directory are accepted. The check is on the path itself (the filesystem is not touched, the
// core does not have to see the files): the path is normalized, so "/base/a/../b" becomes "/base/b"
// and "/base/../etc/passwd" is refused.
@Component
public class WordlistPathPolicy {

	private final Path baseDirectory;

	public WordlistPathPolicy(@Value("${com.automata.wordlist.base-directory}") String baseDirectory) {

		if (baseDirectory == null || baseDirectory.isBlank())
			throw new IllegalStateException("The wordlists base directory is not configured");

		Path base = Path.of(baseDirectory).normalize();

		if (!base.isAbsolute())
			throw new IllegalStateException("The wordlists base directory must be an absolute path: " + baseDirectory);

		this.baseDirectory = base;

	}

	// The normalized path when it is a file path inside the base directory, otherwise IllegalArgumentException
	public String requireInsideBaseDirectory(String path) {

		if (path == null || path.isBlank())
			throw new IllegalArgumentException("path is required");

		Path wordlistPath;
		try {
			wordlistPath = Path.of(path);
		} catch (InvalidPathException ex) {
			throw new IllegalArgumentException("path is not a valid path");
		}

		if (!wordlistPath.isAbsolute())
			throw new IllegalArgumentException("path must be an absolute path");

		Path normalized = wordlistPath.normalize();

		// The base directory itself is not a wordlist
		if (!normalized.startsWith(baseDirectory) || normalized.equals(baseDirectory))
			throw new IllegalArgumentException("path must be inside " + baseDirectory);

		return normalized.toString();

	}

}
