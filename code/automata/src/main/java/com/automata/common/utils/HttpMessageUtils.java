package com.automata.common.utils;

import java.util.Arrays;

public abstract class HttpMessageUtils {

	// The head is the start line, the headers and the blank line ending them. The
	// body is everything after it.
	public record SplitMessage(byte[] head, byte[] body) {
	}

	// Works on bytes so that binary bodies are never touched by a charset. Both CRLF
	// and bare LF line endings are accepted.
	public static SplitMessage split(byte[] rawMessage) {

		int lineStart = 0;

		for (int i = 0; i < rawMessage.length; i++) {

			if (rawMessage[i] != '\n')
				continue;

			int lineEnd = (i > lineStart && rawMessage[i - 1] == '\r') ? i - 1 : i;

			// a blank line (never the very first line) ends the head
			if (lineEnd == lineStart && lineStart != 0) {
				return new SplitMessage(Arrays.copyOfRange(rawMessage, 0, i + 1),
						Arrays.copyOfRange(rawMessage, i + 1, rawMessage.length));
			}

			lineStart = i + 1;

		}

		return new SplitMessage(rawMessage.clone(), new byte[0]);

	}

	// The body as stored in the raw table: null when there is no body, or when the
	// body is stored as a file instead
	public static String encodeInlineBody(byte[] body, boolean storedAsFile) {

		if (storedAsFile || body == null || body.length == 0)
			return null;

		return Base64Utils.encode(body);

	}

}
