package com.automata.wordlist;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// What the core knows about the file of a wordlist: its SHA-256 (lower case hex), size in bytes
// and number of lines. A line is ended by "\n", the last one may be left open ("a\nb" has 2 lines).
public record WordlistFileInfo(String sha256, long sizeBytes, int numberOfLines) {

	// Reads the file once. A file that is empty or has a NUL byte (not text) is refused.
	public static WordlistFileInfo of(Path file) throws IOException {

		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}

		long size = 0;
		long lines = 0;
		int lastByte = -1;

		try (InputStream in = Files.newInputStream(file)) {

			byte[] buffer = new byte[64 * 1024];
			int read;

			while ((read = in.read(buffer)) != -1) {

				digest.update(buffer, 0, read);
				size += read;

				for (int i = 0; i < read; i++) {
					if (buffer[i] == 0)
						throw new IllegalArgumentException("the file is not a text file");
					if (buffer[i] == '\n')
						lines++;
				}

				lastByte = buffer[read - 1];
			}
		}

		if (size == 0)
			throw new IllegalArgumentException("the file is empty");

		if (lastByte != '\n')
			lines++;

		if (lines > Integer.MAX_VALUE)
			throw new IllegalArgumentException("the file has too many lines");

		return new WordlistFileInfo(HexFormat.of().formatHex(digest.digest()), size, (int) lines);

	}

}
