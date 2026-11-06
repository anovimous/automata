package com.automata.common.utils;

import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.automata.common.dto.BodyStorage;
import com.automata.common.dto.HeaderEntry;

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

	// The start line of a head ("GET /a HTTP/1.1"), without its line ending
	public static String startLine(byte[] head) {

		List<String> lines = headLines(head);

		return lines.isEmpty() ? "" : lines.get(0);

	}

	// The request target of a request head: the second token of its start line, null
	// when the line does not have one
	public static String requestTarget(byte[] head) {

		String[] tokens = startLine(head).trim().split("\\s+");

		return tokens.length >= 2 ? tokens[1] : null;

	}

	// The headers of a head in the order they were sent. A line that starts with a
	// space or tab continues the value of the header before it.
	public static List<HeaderEntry> parseHeaders(byte[] head) {

		List<String> lines = headLines(head);

		List<HeaderEntry> headers = new ArrayList<>();

		for (int i = 1; i < lines.size(); i++) {

			String line = lines.get(i);

			if (line.isEmpty())
				break;

			if ((line.charAt(0) == ' ' || line.charAt(0) == '\t') && !headers.isEmpty()) {
				HeaderEntry previous = headers.remove(headers.size() - 1);
				headers.add(new HeaderEntry(previous.name(), previous.value() + " " + line.trim()));
				continue;
			}

			int separator = line.indexOf(':');

			if (separator <= 0)
				continue;

			headers.add(new HeaderEntry(line.substring(0, separator).trim(), line.substring(separator + 1).trim()));

		}

		return headers;

	}

	// The value of the first header with the given name (case-insensitive), null when
	// there is none
	public static String headerValue(List<HeaderEntry> headers, String name) {

		for (HeaderEntry header : headers)
			if (header.name().equalsIgnoreCase(name))
				return header.value();

		return null;

	}

	public static String contentTypeOfHead(byte[] head) {

		return headerValue(parseHeaders(head), "Content-Type");

	}

	public static BodyStorage bodyStorageOf(long bodySizeBytes, boolean storedAsFile) {

		if (bodySizeBytes <= 0)
			return BodyStorage.NONE;

		return storedAsFile ? BodyStorage.FILE : BodyStorage.INLINE;

	}

	private static List<String> headLines(byte[] head) {

		String text = new String(head, StandardCharsets.UTF_8);

		List<String> lines = new ArrayList<>();

		for (String line : text.split("\n", -1))
			lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);

		return lines;

	}

	// The body as stored in the raw table: null when there is no body, or when the
	// body is stored as a file instead
	public static String encodeInlineBody(byte[] body, boolean storedAsFile) {

		if (storedAsFile || body == null || body.length == 0)
			return null;

		return Base64Utils.encode(body);

	}

	/**
	 * A decoder for the head of a message given to the Apache parsers, which would
	 * otherwise read every byte as one character. A head which is not valid UTF-8 is
	 * refused instead of being replaced, since it could not be sent back as it was.
	 */
	public static CharsetDecoder strictUtf8Decoder() {
		return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
	}

	// The encoder which writes a head back as the bytes it was read from
	public static CharsetEncoder utf8Encoder() {
		return StandardCharsets.UTF_8.newEncoder();
	}

	/**
	 * The message of a validation error for an exception of an Apache parser: a
	 * head which is not valid UTF-8 says so, the others keep their own message.
	 */
	public static String headErrorMessage(Exception e, String what) {

		for (Throwable cause = e; cause != null; cause = cause.getCause())
			if (cause instanceof CharacterCodingException)
				return "The " + what + " head is not valid UTF-8";

		return e.getMessage();

	}

}
